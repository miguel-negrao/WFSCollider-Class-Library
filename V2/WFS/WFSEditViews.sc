/*
These are basic editors for WFSPath2 and (WFS-)Points.
use classes:
	WFSPathView( parent, bounds, object ); // object: a WFSPath2
	WFSPathTimeView( parent, bounds, object );  // object: a WFSPath2
	WFSPointView( parent, bounds, object );  // object: a Point or Array of Points
	WFSPlaneView( parent, bounds, object );  // object: a Point or Array of Points
*/

WFSBasicEditView {
	
	var <object, <view;
	
	var <selected, <allSelected = false;
	
	var <>action;
	
	var <selectRect, <hitIndex, <moveHitPoint;
	var <prevMouseMode;
	var <hitPoint, <lastPoint, <optionOn = false;
	
	var <>drawMode = 0; // 0: points+lines, 1: lines, 2: points, 3: none, 4: hi-res lines
	var <>showControls = false;
	
	var <mouseMode = \select; // \select, \zoom, \move (move canvas)
	var <editMode = \move; // \move, \scale, \rotate, \rotateS, \none
	var mouseEdit = false, externalEdit = false;
	
	var <gridColor;
	
	var <>stepSize = 0.1;
	var <>round = 0;
	
	var <undoManager;
	
	*new { |parent, bounds, object|
		^this.newCopyArgs(object).init.makeView( parent, bounds ).setDefaults;
	}
	
	init { // subclass might want to init
	}
	
	setDefaults {
		object = object ?? { this.defaultObject };
	}
	
	makeView { |parent, bounds|
		
		gridColor = gridColor ?? { Color.white.alpha_(0.25) };
		
		view = ScaledUserView.withSliders( parent, bounds, Rect(-100, -100, 200, 200 ) )
			.scaleSliderLength_( 40 )
			.sliderWidth_( 10 )
			.move_( [0.5,0.5] )
			.scale_( [10,10] )
			.maxZoom_( 20 )
			.keepRatio_( true )
			.resize_(5)
			.gridLines_( [ 200, 200 ] )
			.gridMode_( \lines )
			.gridColor_( gridColor );
			
		view.mouseDownAction = { |vw, x,y, mod, oX, oY, isInside, bn, cc|
			var scaler, includes;
			
			scaler = vw.pixelScale;
			mod = ModKey(mod);
			mouseEdit = false;
			
			hitPoint = (x@y);
			lastPoint = hitPoint;
			hitIndex = this.getNearestIndex( hitPoint * (1@ -1), scaler );
			includes = selected.asCollection.includes( hitIndex );
			
			if( bn == 1 ) { // always select with right button
				prevMouseMode = mouseMode;
				if( mouseMode == \select ) {
						this.mouseMode = \zoom;
				} {	
						this.mouseMode = \select;
				};
			}; 
			
			if( mod.alt && includes.not ) {
				prevMouseMode = prevMouseMode ? mouseMode;
				this.mouseMode = \move;
			};
			
			switch( mouseMode, 
				\select, {
					if( cc == 2 ) { this.zoomToFit; };
					if( mod.shift ) { 
						if( hitIndex.notNil ) { 
							if( includes ) { 
								selected.remove( hitIndex ); 
								this.select( selected ); 
							} { 
								this.select( selected.add( hitIndex ) ); 
							}; 
						}; 
					} { 
						if( hitIndex.notNil ) { 
							if( includes.not ) { 
								this.select( hitIndex ) 
							};
						} { 
							this.select() 
						};
					};
				}, \move, {
					if( cc == 2 ) { vw.movePixels = [0,0]; }; // double click
					moveHitPoint = vw.movePixels.asPoint - (oX@oY);
					if( includes.not ) { hitIndex = nil };
				}, \zoom, {
					
					if( includes.not ) { hitIndex = nil };
					if( hitIndex.isNil ) {
						case { 
							mod.shift 
						} { 
							this.zoomIn; 
						} { 
							mod.ctrl 
						} {
							this.zoomOut;
						} { 
							cc == 2 	
						} { 
							this.zoomToFit;
						};
					};
				}, \record, {
					hitIndex = nil;
					this.edited( \start_record );
					selected = [];
					this.startRecord( (x@y) * (1 @ -1), true );
				});
				
			if( hitIndex.notNil ) { 
				this.changed( \mouse_down );
			};
				
			vw.refresh;
		};
		
		view.mouseMoveAction = { |vw, x,y, mod, oX, oY|
			var newPoint, pts, tms;
			mod = ModKey(mod);
			
			newPoint = (x@y);
				
			if( mouseMode === \record ) {
				this.recordPoint( (x@y) * (1@ -1) );
				this.refresh;
			} {
				if( hitIndex.isNil ) {
					switch( mouseMode,
						\select, {	
							
							
							// move canvas if out of bounds
							if( vw.viewRect.contains( newPoint ).not ) {
								{ 
									view.viewRect =   // change to moving/not scaling later?
										view.viewRect.union( 
											Rect.fromPoints( newPoint, newPoint ) 
										).sect( vw.fromBounds ); 
								}.defer(0.5); // 0.4s delay 
							} {	
								  // no point hit -> change selection
								selectRect = Rect.fromPoints( hitPoint, newPoint )
									.scale(1@(-1));
								
								pts = this.getIndicesInRect( selectRect );
								
								if( mod.shift ) {
									this.addToSelection( pts );
								} { 
									this.selectNoUpdate(pts);
								}; 
							};
						}, \move, { 
							view.movePixels_( moveHitPoint + (oX@oY) ); 
						}, \zoom, {
							if( hitPoint.notNil ) { 
								newPoint = (x@y);	
								if( hitPoint.dist( newPoint ) > 1 ) { 
									selectRect = Rect.fromPoints( hitPoint, newPoint )
										.scale(1@(-1)); 
								} { 
									selectRect = nil 
								};
							};
						}, \record, {
							this.recordPoint( (x@y) * (1@ -1) );
							this.refresh;
						}
					)	
				} {
					 // selected point hit -> edit contents
					if( editMode != \none ) {	
						if( externalEdit ) {
							this.edited( \mouse_edit );
							externalEdit = false;
						};
						if( mod.option && { optionOn.not }) { 
							this.duplicateSelected;
							optionOn = true;
						};
						mouseEdit = true;
						this.mouseEditSelected( newPoint );
					} {
						mouseEdit = false;
					};	
				};
			};
			
			lastPoint = newPoint;
		};
		
		
		view.mouseUpAction = { |vw, x, y, mod|
				
				if( mouseMode == \zoom ) { 
					 if( hitPoint.notNil ) { 
						 if( selectRect.notNil ) { 
							 this.zoomToRect( selectRect );
						} /* { 
							mod = ModKey( mod );
							case { 
								mod.shift 
							} { 
								this.zoomIn; 
							} { 
								mod.ctrl 
							} {
								this.zoomOut;
							};
						}; */
					}; 
				};
				
				optionOn = false;
				
				if( mouseMode == \record ) { 
					this.endRecord;
					this.mouseMode = \select; 
				} {	
					if( mouseEdit ) { 
						mouseEdit = false;
						this.edited( \mouse_edit, editMode );
					};
					selectRect = nil;
					hitPoint = nil;
				};
				
				vw.refresh;
				
				if( prevMouseMode.notNil ) {
					this.mouseMode = prevMouseMode;
					prevMouseMode = nil;
				};
		};
		
		
		view.keyDownAction = { |vw, char, modifiers, unicode, keycode|
			var dict;
			
			if( editMode != \none ) {	
				dict = (
					127: \backspace, 
					63234: \leftArrow, 
					63235: \rightArrow,
					63232: \upArrow, 
					63233: \downArrow
				);
				 
				switch( dict[ unicode ],
					\backspace, { 
						this.removeSelected;
					},
					\leftArrow, { 
						if( selected.size == 0 ) { this.select(\all) };
						this.moveSelected( stepSize.neg, 0 )
					},
					\rightArrow, { 
						if( selected.size == 0 ) { this.select(\all) };
						this.moveSelected( stepSize, 0 )
					},
					\upArrow, { 
						if( selected.size == 0 ) { this.select(\all) };
						this.moveSelected( 0, stepSize )
					},
					\downArrow, { 
						if( selected.size == 0 ) { this.select(\all) };
						this.moveSelected( 0, stepSize.neg ) 
					}
				);
			};
		};
		
		view.drawFunc = { |vw|			
			this.drawContents(  vw.pixelScale );
		};
			
		view.unscaledDrawFunc = { |vw|
			var rect;
			
			/// border
			if( vw.view.hasFocus ) { Pen.width = 3; } { Pen.width = 1 };
			Pen.color = Color.gray(0.2).alpha_(0.75);
			Pen.strokeRect( vw.drawBounds.insetBy(0.5,0.5) );
			
			//// selection
			if( selectRect.notNil ) { 
				Pen.width = 1;
				rect = selectRect.scale(1@(-1));
				rect = vw.translateScale(rect);
				switch( mouseMode,
					\select, {
					  //Pen.fillColor = selectColor.copy.alpha_(0.05); 
					  // Pen.strokeColor = selectColor.copy.alpha_(0.5); 
					  Pen.fillColor = Color.black.alpha_(0.05); 
					  Pen.strokeColor = Color.black.alpha_(0.25);
					  Pen.lineDash_( FloatArray[4, 4] );
					},
					\zoom, {
					  Pen.fillColor = Color.black.alpha_(0.05); 
					  Pen.strokeColor = Color.black.alpha_(0.25); 
					});
					
				Pen.addRect( rect ).fillStroke;
			};
		};	
	}
	
	refresh {
		if( view.view.isClosed.not ) { view.refresh };
	}
	
	onClose { ^view.onClose }
	onClose_ { |func| view.onClose = func }
	
	close { // close enclosing window
		view.view.getParents.last.findWindow.close;
	}
	
	front { // close enclosing window
		view.view.getParents.last.findWindow.front;
	}

	
	isClosed { ^view.view.isClosed }
		
	undoManager_ { |um, addFirstState = true|
		undoManager = um;
		if( addFirstState ) { this.edited( \new_object ); }; // force first undo state
	}
	
	handleUndo { |obj|
		if( obj.notNil ) {
			object.positions = obj.positions;
			object.forceTimes( obj.times );
			externalEdit = true;
			this.refresh;
			this.edited( \undo, \no_undo );
		};
	}
	
	undo { |numSteps = 1|
		if( undoManager.notNil ) {
			this.handleUndo( undoManager.undo( numSteps ) );
		};
	}
	
	edited { |what ... moreArgs| // creates undo state, calls action and changed
		if( undoManager.notNil ) {
			if( moreArgs.includes( \no_undo ).not ) { 
				undoManager.add( this.object, ([ what ] ++ moreArgs).join("_").asSymbol );
			};
		};
		action.value( this );
		this.changed( what, *moreArgs );		
	}
	
	zoomToFit { |includeCenter = true|
		if( includeCenter ) { 
			view.viewRect_( object.asRect.scale(1@(-1))
				.union( Rect(0,0,0,0) ).insetBy(-1,-1) );  
		} { 
			view.viewRect_( object.asRect.scale(1@(-1)).insetBy(-1,-1) ); 
		};
	}
	
	zoomToRect { |rect|
		rect = rect ?? { 
			object.asRect.union( Rect(0,0,0,0) ).insetBy(-1,-1) 
		};
		view.viewRect = rect.scale(1@(-1));
	}
	
	zoomIn { |amt|
		amt = amt ?? { 2.sqrt };
		view.scale = view.scale * amt;
	}
	
	zoomOut { |amt|
		amt = amt ?? { 2.sqrt };
		view.scale = view.scale / amt;
	}
	
	zoom { |level = 1|
		view.scale = level*10;
	}
	
	move { |x,y|
		x = x ? 0;
		y = y ? x;
		view.move_([x,y].linlin(-100,100,0,1));
	}
	
	moveToCenter { 
		view.move_([0.5,0.5]);
	}
	
	object_ { |newPath, active = true| 
			if( object != newPath ) {
				object = newPath;
				this.refresh;
				if( active ) { 
					this.edited( \new_object ); 				} {
					this.changed( \new_object ); 
				};
			};
	}
	
	doAction { action.value( this ) }
	
	mouseMode_ { |newMode|
		newMode = newMode ? \select;
		if( mouseMode != newMode ) {
			mouseMode = newMode;
			this.changed( \mouseMode );
		};
	}
	
	editMode_ { |newMode|
		newMode = newMode ? \move;
		if( editMode != newMode ) {
			editMode = newMode;
			this.changed( \editMode );
		}
	}
	
	gridColor_ { |aColor|
		gridColor = aColor ?? { Color.white.alpha_(0.25) };
		view.gridColor = aColor;
	}
	
		addToSelection { |...indices|
		 this.select( *((selected ? []).asSet.addAll( indices ) ).asArray );
	}
	
	selectAll { this.select( \all ) }
	selectNone { this.select( ) }
}

//////// PATH EDITOR /////////////////////////////////////////////////////////////////

WFSPathXYView : WFSBasicEditView {
	
	var <pos; 
	var <recordLastTime;
	var <animationTask, <>animationRate = 1;

	defaultObject	{ ^WFSPath2( { (8.0@8.0).rand2 } ! 7, [0.5] ); }	
	mouseEditSelected { |newPoint|
		var pt;
		// returns true if changed
		switch( editMode,
			\move,  { 
				pt = (newPoint.round(round) - lastPoint.round(round)) * (1@(-1));
				this.moveSelected( pt.x, pt.y, \no_undo );
			},
			\scale, { 
				pt = [ lastPoint.round(round).abs.max(0.001) * 
						lastPoint.asArray.collect({ |item|
							(item > 0).binaryValue.linlin(0,1,-1,1)
						}).asPoint,
					  newPoint.round(round).abs.max(0.001) * 
						newPoint.asArray.collect({ |item|
							(item > 0).binaryValue.linlin(0,1,-1,1)
						}).asPoint
				]; // prevent inf/nan
				pt = pt[1] / pt[0];
				this.scaleSelected( pt.x, pt.y, \no_undo ); 
			},
			\rotate, { 
				this.rotateSelected( 
					lastPoint.angle - newPoint.angle, 
					1, 
					\no_undo
				);
			},
			\rotateS, { 
				this.rotateSelected( 
					lastPoint.theta - newPoint.theta, 
					newPoint.rho.max(0.001) / lastPoint.rho.max(0.001), 
					\no_undo
				);
			}
		);
	}
	
	
	drawContents { |scale = 1|
		var points, controls;
		
		scale = scale.asArray.mean;
		
		Pen.use({	
			
			Pen.width = 0.164;
			Pen.color = Color.red(0.5, 0.5);
				
			//// draw configuration
			(WFSSpeakerConf.default ?? {
				WFSSpeakerConf.rect(48,48,5,5);
			}).draw;
				
			// draw center
			Pen.line( -0.25 @ 0, 0.25 @ 0 ).line( 0 @ -0.25, 0 @ 0.25).stroke;
			
			object.draw( drawMode, selected, pos, showControls, scale );
			
		});
		
	}
	
	getNearestIndex { |point, scaler| // returns nil if outside radius
		var radius;
		radius = scaler.asArray.mean * 5;
		^object.positions.detectIndex({ |pt, i|
			pt.asPoint.dist( point ) <= radius
		});
	}
	
	getIndicesInRect { |rect|
		var pts = [];
		object.positions.do({ |pt, i|
			if( rect.contains( pt.asPoint ) ) { pts = pts.add(i) };
		});
		^pts;					
	}
	
	// general methods
	
	resize { ^view.resize }
	resize_ { |resize| view.resize = resize }
	
	path_ { |path| this.object = path }
	path { ^object }
	
	pos_ { |newPos, changed = true|
		pos = newPos;
		{ this.refresh; }.defer; // for animation
		if( changed ) { this.changed( \pos ); };
	}
	
	// changing the object
	
	moveSelected { |x = 0,y = 0 ...moreArgs|
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt;
				pt = object.positions[ index ];
				pt.x = pt.x + x;
				pt.y = pt.y + y;
			});
			this.refresh; 
			this.edited( \edit, \move, *moreArgs );
		};
	}
	
	scaleSelected { |x = 1, y ...moreArgs|
		y = y ? x;
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt;
				pt = object.positions[ index ];
				pt.x = pt.x * x;
				pt.y = pt.y * y;
			});
			this.refresh;
			this.edited( \edit, \scale, *moreArgs );
		};
	}
	
	rotateSelected { |angle = 0, scale = 1 ...moreArgs|
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt, rpt;
				pt = object.positions[ index ];
				rpt = pt.rotate( angle ) * scale;
				pt.x = rpt.x;
				pt.y = rpt.y;
			});
			this.refresh;
			this.edited( \edit, \rotate, *moreArgs );
		};
	}
	
	duplicateSelected { 
		var points, times, index;
		if( selected.size >= 1 ) {
			selected = selected.sort;
			points = object.positions[ selected ].collect(_.copy);
			times = object.times[ selected ];
			index = selected.maxItem + 1;
			selected = object.insertMultiple( index, points, times );
			this.refresh;
			this.edited( \duplicateSelected );
		};
	}
	
	removeSelected {
		var times;
		times = object.times;
		selected.do({ |item, i|
			var addTime;
			addTime = times[ item ];
			if( addTime.notNil && (item != 0) ) {
				times[item-1] = times[item-1] + addTime;
			};
		});
		object.positions = object.positions.select({ |item, i|
			selected.includes(i).not;
		});
		object.forceTimes( 
			times.select({ |item, i|
				selected.includes(i).not;
			}).collect( _ ? 0.1 )
		);
		selected = [];
		this.refresh;
		this.edited( \removeSelected );
	}
	
	// selection
	
	select { |...indices|
		if( indices[0] === \all ) { 
			indices = object.positions.collect({ |item, i| i }).flat; 
		} { 
			indices = indices.flat.select(_.notNil);
		};
		if( selected != indices ) {
			selected = indices; 
			this.refresh;
			this.changed( \select );
		};
	}
	
	selectNoUpdate { |...index|
		if( index[0] === \all ) { 
			index = object.positions.collect({ |item, i| i }).flat 
		} {
			index = index.flat.select(_.notNil);
		};
		if( selected != index ) {
			selected = index;
			this.changed( \select ); 
		};
	}
	
	// animation
	
	animate { |bool = true, startAt|
		var res = 0.05;
		animationTask.stop;
		if( bool ) {
			this.pos = pos ? startAt ? 0;
			animationTask = Task({
				while { pos.inclusivelyBetween(0, object.length) } {
					res.wait;
					this.pos = pos + (res * animationRate);
				};
				this.pos = nil;
				this.changed( \animate, false );
			}).start;
		} {
			this.pos = nil;
		};
		this.changed( \animate, bool );
	}
	
	
	
	// recording support
	
	startRecord { |point, clearPath = true, addTime = 0.1|
		recordLastTime = Process.elapsedTime;
		if( clearPath ) { 
			object.positions = [ point.asWFSPoint ];
			object.forceTimes([]);
		} {
			object.positions = object.positions ++ [ point.asWFSPoint ];
			object.forceTimes( object.times ++ [ addTime ] );
		};
	}
	
	recordPoint { |point| // adds point to end
		var newTime, delta;
		if( recordLastTime.notNil ) { // didn't start recording yet
			newTime = Process.elapsedTime;
			object.forceTimes( object.times ++ [ newTime - recordLastTime ] );
			object.positions = object.positions ++ [ point.asWFSPoint ];
			recordLastTime = newTime;
		} { 
			"%: didn't start recording yet\n".postf( thisMethod ); 
		};	
	}
			
	endRecord {
		recordLastTime = nil;
		this.edited( \endRecord );
	}
}

//////// PATH TIMELINE EDITOR /////////////////////////////////////////////////////////////////

WFSPathTimeView : WFSPathXYView {
	
	setDefaults {
		object = object ?? { this.defaultObject };
		view.fromBounds_( Rect( 0, -0.5, 1, 1 ) )
			.gridLines_( [ inf, inf ] )
			.scale_( 1 )
			.moveVEnabled_( false )
			.scaleVEnabled_( false )
			.keepRatio_( false );
	}
	
	
	defaultObject	{ ^WFSPath2( { (8.0@8.0).rand2 } ! 7, [0.5] ); }

	drawContents { |scale = 1|
		var times, speeds, timesSum, meanSpeed;
		var drawPoint;
		var selectColor = Color.yellow;
		var pospt;
		
		scale = scale.asPoint;
		
		drawPoint = { |point, r = 3, w = 1|
			Pen.addOval( 
				Rect.aboutPoint( point, scale.x * r, scale.y * r ) );
			Pen.addOval( 
				Rect.aboutPoint( point, scale.x * (r-(w/2)) , scale.y * (r-(w/2)) ) );
		};
		
		if( object.times.size > 0 ) {	
			timesSum = this.getTimesSum;
			times = ([ 0 ] ++ object.times.integrate) / timesSum;
			
			
			speeds = object.speeds;
			meanSpeed = (speeds * object.times).sum / timesSum;
			speeds = speeds ++ [0];
			
			Pen.color = Color.blue(0.5).blend( Color.white, 0.5 );
			times.do({ |item, i|
				//Pen.color = Color.red(0.75).alpha_( (speeds[i] / 334).min(1) );
				Pen.addRect( 
					Rect( item, 0.5, times.clipAt(i+1) - item, speeds[i] / -344));
							
			});
			Pen.fill;	
						
			Pen.color = Color.gray(0.25); // line
			Pen.addRect(Rect( 0, 0 - (scale.y/4), times.last, scale.y/2 ) ).fill;
		
			Pen.color = Color.green(0.5,0.5); // start point
			Pen.addOval( Rect.aboutPoint( times[0]@0, 
				scale.x * 5, scale.y * 5 ) );		
			Pen.fill;
				
			Pen.color = Color.red(1, 0.5); // end point
			Pen.addOval( Rect.aboutPoint( times.last@0, 
				scale.x * 5, scale.y * 5 ) );		
			Pen.fill;
			
			Pen.color = selectColor; // selected points
			selected.do({ |item| 
				Pen.addOval( Rect.aboutPoint( times[item]@0, 
					scale.x * 3.5, scale.y * 3.5 ) );
			});
			Pen.fill;
			
			if( pos.notNil ) {
				pospt = pos / timesSum;
				Pen.color = Color.black.alpha_(0.5);
				Pen.width = scale.x * 2;
				Pen.line( pospt @ -0.5, pospt @ 0.5 ).stroke;
			};
			
			Pen.color = Color.blue(0.5);
			times[1..].do({ |item, i| drawPoint.( item@0 ); });
			Pen.draw(1);
		};
	}
	
	zoomToFit { |includeCenter = true|
		view.scale = 1;
		view.move = 0.5;
	}
	
	zoomToRect { |rect|
		rect = (rect ?? { Rect( 0, -0.5, 1, 1 ) }).copy;
		rect.top = -0.5;
		rect.height = 1;
		view.viewRect = rect;
	}
	
	zoomIn { |amt|
		amt = amt ?? { 2.sqrt };
		view.scale = view.scale * amt;
	}
	
	zoomOut { |amt|
		amt = amt ?? { 2.sqrt };
		view.scale = view.scale / amt;
	}
	
	zoom { |level = 1|
		view.scale = level;
	}
	
	move { |x,y|
		x = x ? 0;
		view.move_(x);
	}
	
	moveToCenter { 
		view.move_([0.5,0.5]);
	}
	
	getTimesSum { ^object.times.sum }
	
	getNearestIndex { |point, scaler| // returns nil if outside radius
		var times, rect;
		times = (([ 0 ] ++ object.times.integrate) / this.getTimesSum);
		rect = Rect.aboutPoint( point, scaler.x * 5, scaler.y * 5 );
		^times.detectIndex({ |t, i|
			rect.contains( t@0 );
		});
	}
	
	getIndicesInRect { |rect|
		var pts = [], times;
		times = ([ 0 ] ++ object.times.integrate) / this.getTimesSum;
		times.do({ |t, i|
			if( rect.contains( t@0 ) ) { pts = pts.add(i) };
		});
		^pts;					
	}
	
	mouseEditSelected { |newPoint|
		var pt;
		// returns true if edited
		switch( editMode,
			\move,  { 
				pt = (newPoint.round(round) - lastPoint.round(round));
				this.moveSelected( pt.x, pt.y, \no_undo );
			}
		);
	}

	moveSelected { |x = 0, y = 0 ...moreArgs|
		var timesPositions;
		var moveAmt;
		if( (selected.size > 0) ) {
			moveAmt = x * this.getTimesSum;
			
			timesPositions = [ 
				[ 0 ] ++ object.times.integrate, 
				object.positions,
				object.positions.collect({ |item, i| selected.includesEqual(i) })
			].flop;
			
			selected.do({ |index|
				timesPositions[ index ][0] = timesPositions[ index ][0] + moveAmt;
			});
			
			timesPositions = timesPositions.sort({ |a,b| a[0] <= b[0] }).flop;
			object.positions = timesPositions[1];
			object.forceTimes((timesPositions[0]).differentiate[1..]);
			selected = timesPositions[2].indicesOfEqual( true );
			this.refresh;
			this.edited( \edit, \move, *moreArgs );
		};
	}
	
	scaleSelected { |x = 1, y ...moreArgs|
		y = y ? x;
		if( selected.size > 0 ) {
			selected.do({ |index|
				object.positions[ index ] = object.positions[ index ] * (x@y);
			});
			this.refresh;
			this.edited( \edit, \scale, *moreArgs );
		};
	}
	
	rotateSelected { |angle = 0, scale = 1, update = true|
		// can't rotate times
	}

	
}

//////// POINT EDITOR /////////////////////////////////////////////////////////////////

WFSPointView : WFSBasicEditView {
	
	var <>canChangeAmount = true;
	var <showLabels = true;
	var <labels;
	
	// object is an array of points

	defaultObject	{ ^[ Point(0,0) ]	 }	
	
	mouseEditSelected { |newPoint|
		var pt;
		// returns true if edited
		switch( editMode,
			\move,  { 
				pt = (newPoint.round(round) - lastPoint.round(round)) * (1@(-1));
				this.moveSelected( pt.x, pt.y, false );
			},
			\scale, { 
				pt = [ lastPoint.round(round).abs.max(0.001) * 
						lastPoint.asArray.collect({ |item|
							(item > 0).binaryValue.linlin(0,1,-1,1)
						}).asPoint,
					  newPoint.round(round).abs.max(0.001) * 
						newPoint.asArray.collect({ |item|
							(item > 0).binaryValue.linlin(0,1,-1,1)
						}).asPoint
				]; // prevent inf/nan
				pt = pt[1] / pt[0];
				this.scaleSelected( pt.x, pt.y, false ); 
			},
			\rotate, { 
				this.rotateSelected( 
					lastPoint.angle - newPoint.angle, 
					1, 
					false
				);
			},
			\rotateS, { 
				this.rotateSelected( 
					lastPoint.theta - newPoint.theta, 
					newPoint.rho.max(0.001) / lastPoint.rho.max(0.001), 
					false
				);
			}
		);
	}
	
	showLabels_ { |bool| 
		showLabels = bool; 
		this.refresh; 
		this.changed( \showLabels ); 
	}
	
	labels_ { |array| 
		labels = array.asCollection;
		this.refresh; 
		this.changed( \labels ); 
	}
	
	
	drawContents { |scale = 1|
		var points, controls;
		var selectColor = Color.yellow;
		
		scale = scale.asArray.mean;
		
		Pen.use({	
			
			Pen.width = 0.164;
			Pen.color = Color.red(0.5, 0.5);
				
			//// draw configuration
			(WFSSpeakerConf.default ?? {
				WFSSpeakerConf.rect(48,48,5,5);
			}).draw;
				
			// draw center
			Pen.line( -0.25 @ 0, 0.25 @ 0 ).line( 0 @ -0.25, 0 @ 0.25).stroke;
			
			Pen.scale(1,-1);
			
			points = object.asCollection.collect(_.asPoint);
			
			Pen.width = scale;
		
			Pen.color = Color.blue(0.5,0.75);
			points.do({ |item|
					Pen.moveTo( item );
					Pen.addArc( item, 3 * scale, 0, 2pi );
					Pen.line( item - ((5 * scale)@0), item + ((5 * scale)@0));
					Pen.line( item - (0@(5 * scale)), item + (0@(5 * scale)));
			});
			Pen.stroke;	
		
			// selected
			Pen.use({	
				if( selected.notNil ) {	
					Pen.width = scale;
					Pen.color = selectColor;
					selected.do({ |item|
						Pen.moveTo( points[item] );
						Pen.addArc( points[item] , 2.5 * scale, 0, 2pi );
					});
					
					Pen.fill;
				};
			});
			
			if( showLabels && { points.size > 1 } ) {
					Pen.font = Font( Font.defaultSansFace, 9 );
					Pen.color = Color.black;
					points.do({ |item, i|
						Pen.use({
							Pen.translate( item.x, item.y );
							Pen.scale(scale,scale.neg);
							Pen.stringAtPoint( 
								((labels ? [])[i] ? i).asString, 
								5 @ -12 );
						});
					});
			};
			
		});
		
	}
	
	getNearestIndex { |point, scaler| // returns nil if outside radius
		var radius;
		radius = scaler.asArray.mean * 7;
		^object.asCollection.detectIndex({ |pt, i|
			pt.asPoint.dist( point ) <= radius
		});
	}
	
	getIndicesInRect { |rect|
		var pts = [];
		object.asCollection.do({ |pt, i|
			if( rect.contains( pt.asPoint ) ) { pts = pts.add(i) };
		});
		^pts;					
	}
	
	handleUndo { |obj|
		if( obj.notNil ) {
			object = obj;
			externalEdit = true;
			this.refresh;
			this.edited( \undo, \no_undo );
		};
	}
	
	// general methods
	
	resize { ^view.resize }
	resize_ { |resize| view.resize = resize }
	
	point_ { |point| this.object = (object ? [0]).asCollection[0] = point.asPoint }
	point { ^object.asCollection[0] }
	
	at { |index| ^object.asCollection[index] }
	
	zoomToFit { |includeCenter = true|
		var x,y;
		#x, y = object.collect({ |item| item.asArray }).flop;
		if( includeCenter ) { 
			view.viewRect_( Rect.fromPoints( x.minItem @ y.minItem, x.maxItem @ y.maxItem )
				.union( Rect(0,0,0,0) ).insetBy(-5,-5) );  
		} { 
			view.viewRect_( Rect.fromPoints( x.minItem @ y.minItem, x.maxItem @ y.maxItem )
				.asRect.scale(1@(-1)).insetBy(-5,-5) ); 
		};
	}

		
	// changing the object
	
	moveSelected { |x = 0,y = 0 ...moreArgs|
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt;
				pt = object.asCollection[ index ];
				pt.x = pt.x + x;
				pt.y = pt.y + y;
			});
			this.refresh; 
			this.edited( \edit, \move );
		};
	}
	
	scaleSelected { |x = 1, y ...moreArgs|
		y = y ? x;
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt;
				pt = object.asCollection[ index ];
				pt.x = pt.x * x;
				pt.y = pt.y * y;
			});
			this.refresh;
			this.edited( \edit, \scale, *moreArgs );
		};
	}
	
	rotateSelected { |angle = 0, scale = 1 ...moreArgs|
		if( selected.size > 0 ) {
			selected.do({ |index|
				var pt, rpt;
				pt = object.asCollection[ index ];
				rpt = pt.rotate( angle ) * scale;
				pt.x = rpt.x;
				pt.y = rpt.y;
			});
			this.refresh;
			this.edited( \edit, \rotate, *moreArgs );
		};
	}
	
	duplicateSelected { 
		var points;
		if( canChangeAmount && { selected.size >= 1} ) {
			selected = selected.sort;
			points = object.asCollection[ selected ].collect(_.copy);
			selected = object.size + (..points.size-1);
			object = object ++ points;
			this.refresh;
			this.edited( \duplicateSelected );
		};
	}
	
	removeSelected {
		if( canChangeAmount && { object.size > selected.size } ) {
			object = object.asCollection.select({ |item, i|
				selected.includes(i).not;
			});
			selected = [];
		} {
			"WFSPointView-removeSelected : should leave at least one point".warn;
		};
		this.refresh;
		this.edited( \removeSelected );
	}
	
	// selection
	
	select { |...indices|
		if( indices[0] === \all ) { 
			indices = object.asCollection.collect({ |item, i| i }).flat; 
		} { 
			indices = indices.flat;
		};
		if( selected != indices ) {
			selected = indices; 
			this.refresh;
			this.changed( \select );
		};
	}
	
	selectNoUpdate { |...index|
		if( index[0] === \all ) { 
			index = object.asCollection.collect({ |item, i| i }).flat 
		} {
			index = index.flat;
		};
		if( selected != index ) {
			selected = index;
			this.changed( \select ); 
		};
	}
	
}

//////// PLANE EDITOR /////////////////////////////////////////////////////////////////

WFSPlaneView : WFSPointView {

	drawContents { |scale = 1|
		var points, controls;
		var selectColor = Color.yellow;
		
		scale = scale.asArray.mean;
		
		Pen.use({	
			
			Pen.width = 0.164;
			Pen.color = Color.red(0.5, 0.5);
				
			//// draw configuration
			(WFSSpeakerConf.default ?? {
				WFSSpeakerConf.rect(48,48,5,5);
			}).draw;
				
			// draw center
			Pen.line( -0.25 @ 0, 0.25 @ 0 ).line( 0 @ -0.25, 0 @ 0.25).stroke;
			
			Pen.scale(1,-1);
			
			points = object.asCollection.collect(_.asPoint);
			
			Pen.width = scale;
		
			Pen.color = Color.blue(0.5,0.75);
			points.do({ |p|
				var polar, p1, p2;
				polar = (p * (1@ 1)).asPolar;
				p1 = polar.asPoint;
				p2 = Polar( 50, polar.angle-0.5pi).asPoint;
				Pen.line( p1 + p2, p1 - p2 ).stroke;
				p2 = Polar( scale * 15, polar.angle ).asPoint;
				Pen.arrow( p1 + p2, p1 - p2, scale * 5 );
			});
			Pen.stroke;
		
			// selected
			Pen.use({	
				if( selected.notNil ) {	
					Pen.width = scale;
					Pen.color = selectColor;
					selected.do({ |item|
						Pen.moveTo( points[item] );
						Pen.addArc( points[item] , 2.5 * scale, 0, 2pi );
					});
					
					Pen.fill;
				};
			});
			
			if( showLabels && { points.size > 1 } ) {
					Pen.font = Font( Font.defaultSansFace, 9 );
					Pen.color = Color.black;
					points.do({ |item, i|
						Pen.use({
							Pen.translate( item.x, item.y );
							Pen.scale(scale,scale.neg);
							Pen.stringAtPoint( 
								((labels ? [])[i] ? i).asString, 
								5 @ -12 );
						});
					});
			};
			
		});
		
	}	
}