WFSPathSpec : Spec {
	
	*testObject { |obj|
		^obj.isKindOf( WFSPathBuffer );
	}
	
	constrain { |value|
		^value;
	}
	
	default { 
		^WFSPathBuffer(  WFSPath2( { (8.0@8.0).rand2 } ! 7, [0.5] ) );
	}
	
	*newFromObject { |obj|
		^this.new;
	}
	
	viewNumLines { ^WFSPathBufferView.viewNumLines }
	
	viewClass { ^WFSPathBufferView }
	
	makeView { |parent, bounds, label, action, resize|
		var vws, view, labelWidth;
		
		vws = ();
		
		bounds.isNil.if{bounds= 350 @ (this.viewNumLines * 18) };
		view = EZCompositeView( parent, bounds, gap: 4@4 );
		bounds = view.view.bounds;
		
		 vws[ \view ] = view;
		 
		if( label.notNil ) {
			labelWidth = (RoundView.skin ? ()).labelWidth ? 80;
			vws[ \labelView ] = StaticText( vws[ \view ], labelWidth @ 14 )
				.string_( label.asString ++ " " )
				.align_( \right )
				.resize_( 4 )
				.applySkin( RoundView.skin );
		} {
			labelWidth = -4;
		};
		
		if( resize.notNil ) { vws[ \view ].resize = resize };
		
		vws[ \wfsPathBufferView ] = this.viewClass.new( vws[ \view ], 
			( bounds.width - (labelWidth+4) ) @ bounds.height, { |vw|
				action.value( vw, vw.value )
			} )
		
		^vws;
	}
	
	setView { |view, value, active = false|
		view[ \wfsPathBufferView ].value = value;
		if( active ) { view.doAction };
	}
	
}

WFSPointSpec : PointSpec {
	
	makeView { |parent, bounds, label, action, resize|
		var vws, view, labelWidth, val = 0@0;
		var localStep;
		var modeFunc;
		vws = ();
		
		localStep = step.copy;
		if( step.x == 0 ) { localStep.x = 1 };
		if( step.y == 0 ) { localStep.y = 1 };
		
		bounds.isNil.if{bounds= 320@20};
		
		view = EZCompositeView( parent, bounds, gap: 4@4 );
		bounds = view.asView.bounds;
				
		vws[ \view ] = view;
		 		
		if( label.notNil ) {
			labelWidth = (RoundView.skin ? ()).labelWidth ? 80;
			vws[ \labelView ] = StaticText( vws[ \view ], labelWidth @ bounds.height )
				.string_( label.asString ++ " " )
				.align_( \right )
				.resize_( 4 )
				.applySkin( RoundView.skin );
		} {
			labelWidth = 0;
		};
		
		vws[ \comp1 ] = CompositeView( view, 40 @ (bounds.height) );
		
		vws[ \xy ] = XYView( view, bounds.height.asPoint )
			.action_({ |xy|
				var newVal, theta;
				newVal = val + (xy.value * localStep * (1 @ -1));
				/*
				theta = newVal.theta;
				vws[ \x ].value = newVal.x;
				vws[ \y ].value = newVal.y;
				vws[ \rho ].value = newVal.rho;
				vws[ \theta ].value = theta / pi;
				vws[ \deg_cw ].value = theta
					.wrap( -1.5pi, 0.5pi)
					.linlin(-1.5pi, 0.5pi, 360, 0 );
				*/
				this.setView( vws, newVal );
				action.value( vws, newVal );
			})
			.mouseUpAction_({
				val = vws[ \x ].value @ vws[ \y ].value;
			});
			
		vws[ \comp2 ] = CompositeView( view, 60 @ (bounds.height) );
		
		vws[ \mode ] = PopUpMenu( view, 60 @ (bounds.height) )
			.font_( Font( Font.defaultSansFace, 10 ) )
			.applySkin( RoundView.skin ? () )
			.items_([ 'point', 'polar', 'deg_cw' ])
			.action_({ |pu|
				mode = pu.item;
				this.setMode( vws, mode );
			});
		
		// point mode
		vws[ \x ] = SmoothNumberBox( vws[ \comp1 ], 40 @ (bounds.height) )
			.action_({ |nb|
				val = nb.value @ val.y;
				this.setView( vws, val );
				action.value( vws, val);
			})
			//.step_( localStep.x )
			.scroll_step_( localStep.x )
			.clipLo_( rect.left )
			.clipHi_( rect.right )
			.value_(0);
			
		vws[ \y ] = SmoothNumberBox( vws[ \comp2 ], 40 @ (bounds.height) )
			.action_({ |nb|
				val = val.x @ nb.value;
				this.setView( vws, val );
				action.value( vws, val );
			})
			//.step_( localStep.y )
			.scroll_step_( localStep.y )
			.clipLo_( rect.top )
			.clipHi_( rect.bottom )
			.value_(0);
		
		// polar, deg_cw
		vws[ \rho ] = SmoothNumberBox( vws[ \comp1 ], 40 @ (bounds.height) )
			.action_({ |nb|
				val = val.asPolar.rho_( nb.value ).asPoint;
				this.setView( vws, val );
				action.value( vws, val );
			})
			//.step_( localStep.x )
			.scroll_step_( localStep.x )
			.clipLo_( 0 )
			.clipHi_( rect.height.max( rect.width ) )
			.value_(0);
		
		// polar	
		// -pi - pi counterclockwise (0 = straight right)
		vws[ \theta ] = SmoothNumberBox( vws[ \comp2 ], 40 @ (bounds.height) )
			.action_({ |nb|
				val = val.asPolar.theta_( nb.value * pi ).asPoint;
				this.setView( vws, val );
				action.value( vws, val );
			})
			.step_( 0.25 )
			.scroll_step_( 0.005 )
			.clipLo_( -1 )
			.clipHi_( 1 )
			.wrap_(true)
			.value_(0);
			
		vws[ \thetaLabel ] = StaticText( vws[ \comp2 ], Rect( 42, 0, 18, bounds.height) )
			.applySkin( RoundView.skin ? () )
			.string_( "pi" );
		
		// deg_cw	
		// 0 - 360 clockwise (0 = straight front)
		vws[ \deg_cw ] = SmoothNumberBox( vws[ \comp2 ], 40 @ (bounds.height) )
			.action_({ |nb|
				val = val.asPolar.theta_( 
					nb.value.neg.linlin(-360,0,-1.5pi,0.5pi)
				).asPoint;
				this.setView( vws, val );
				action.value( vws, val );
			})
			.step_( 1 )
			.scroll_step_( 1 )
			.clipLo_( 0 )
			.clipHi_( 360 )
			.wrap_(true)
			.value_(0);
			
		//vws[ \rotate ] = 
			
		this.setMode( vws, mode );
	
		^vws;
	}
	
	setMode { |view, newMode|
		switch( newMode,
			\point, {
				[ \x, \y ].do({ |item|
					view[ item ].visible = true;
				});
				[ \rho, \theta, \thetaLabel, \deg_cw ].do({ |item|
					view[ item ].visible = false;
				});
			},
			\polar, {
				[ \rho, \theta, \thetaLabel ].do({ |item|
					view[ item ].visible = true;
				});
				[ \x, \y, \deg_cw ].do({ |item|
					view[ item ].visible = false;
				});
			},
			\deg_cw, {
				[ \rho, \deg_cw, \thetaLabel ].do({ |item|
					view[ item ].visible = true;
				});
				[ \x, \y, \theta, \thetaLabel ].do({ |item|
					view[ item ].visible = false;
				});
			}
		);
	}
	
	setView { |view, value, active = false|
		var constrained, theta;
		constrained = this.constrain( value );
		theta = constrained.theta;
		view[ \x ].value = constrained.x;
		view[ \y ].value = constrained.y;
		view[ \rho ].value = constrained.rho;
		view[ \theta ].value = theta / pi;
		view[ \deg_cw ].value = theta
			.wrap( -1.5pi, 0.5pi)
			.linlin(-1.5pi, 0.5pi, 360, 0 );

		this.setMode( view, mode );
		{ view[ \mode ].value = view[ \mode ].items.indexOf( mode ) ? 0; }.defer;
		if( active ) { view[ \x ].doAction };
	}
	
	mapSetView { |view, value, active = false|
		this.setView( view, this.map( value ), active );
	}
	

	
}

