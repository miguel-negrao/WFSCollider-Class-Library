MassEditU : U { // mimicks a real U, but in fact edits multiple instances of the same
	
	var <units, <>argSpecs;
	
	*new { |units| // all units have to be of the same Udef
		^super.newCopyArgs.init( units );
	}
	
	init { |inUnits|
		var firstDef, defs;
		units = inUnits.asCollection;
		defs = inUnits.collect(_.def);
		firstDef = defs[0];
		if( defs.every({ |item| item == firstDef }) ) {
			def = firstDef;
			argSpecs = def.argSpecs.collect({ |argSpec|
				var values, massEditSpec;
				values = units.collect({ |unit|
					unit.get( argSpec.name );
				});
				massEditSpec = argSpec.spec.massEditSpec( values );
				if( massEditSpec.notNil ) {
					ArgSpec( argSpec.name, massEditSpec.default, massEditSpec, argSpec.private ); 
				} {
					nil;
				};
			}).select(_.notNil);
			args = argSpecs.collect({ |item| [ item.name, item.default ] }).flatten(1);
			
		} {
			"MassEditU:init - not all units are of the same Udef".warn;
		};
	}
	
	units_ { |inUnits| this.init( inUnits ); }
	
	resetArg { |key| // doesn't change the units
		if( key.notNil ) {
			this.setArg( key, def.getSpec( key ).massEditValue( 
				units.collect({ |unit| unit.get( key ) }) ) 
			);
		} {
			this.keys.do({ |key| this.resetArg( key ) });
		};
	}
	
	set { |...args|
		var synthArgs;
		args.pairsDo({ |key, value|
			var values;
			//value = value.asUnitArg( this );
			this.setArg( key, value );
			values = def.getSpec( key ).massEdit( units.collect(_.get(key) ), value );
			units.do({ |unit, i|
				unit.set( key, values[i] );
			});
		});
	}
	
	defName { ^((def !? { def.name }).asString + "(% units)".format( units.size )).asSymbol }
	
}


MassEditUChain {
	
	var <uchains;
	var <units;
	var <>prepareTasks;
	
	*new { |uchains|
		^super.newCopyArgs( uchains ).init;
	}
	
	init {
		var allDefNames = [], allUnits = Order();
		
		uchains.do({ |uchain|
			uchain.units.do({ |unit|
				var defName, index;
				defName = unit.defName;
				if( allDefNames.includes( defName ).not ) {
					allDefNames = allDefNames.add( defName );
				};
				index = allDefNames.indexOf( defName );
				allUnits.put( index, allUnits[ index ].add( unit ) );
			});
		});
		
		units = allUnits.asArray.collect({ |item|
			if( item.size == 1 ) {
				item[0];
			} {
				MassEditU( item );
			};
		});
		
		this.changed( \init );
	}
	
	fadeIn_ { |fadeIn = 0|
		var maxFadeIn, mul;
		maxFadeIn = this.fadeIn.max(1.0e-11);
		
		fadeIn = fadeIn.max(1.0e-11); // never 0 to keep original times
		mul = fadeIn / maxFadeIn;
		
		uchains.do({ |item|
			item.fadeIn_( item.fadeIn * mul );
		});
		
		this.changed( \fadeIn );	
	}
	
	fadeOut_ { |fadeOut = 0|
		var maxFadeOut, mul;
		maxFadeOut = this.fadeOut.max(1.0e-11);
		
		fadeOut = fadeOut.max(1.0e-11); // never 0 to keep original times
		mul = fadeOut / maxFadeOut;
		
		uchains.do({ |item|
			item.fadeOut_( item.fadeOut * mul );
		});
		
		this.changed( \fadeIn );	
	}
	
	fadeOut {
		^uchains.collect({ |item| item.fadeOut }).maxItem ? 0;
	}
	
	fadeIn {
		^uchains.collect({ |item| item.fadeIn }).maxItem ? 0;
	}
	
	useSndFileDur { // look for SndFiles in all units, use the longest duration found
		var durs;
		uchains.do(_.useSndFileDur);
	}
	
	getMaxDurChain { // get unit with longest non-inf duration
		var dur, out;
		uchains.do({ |uchain|
			var u_dur;
			u_dur = uchain.dur;
			if( (u_dur > (dur ? 0)) && { u_dur != inf } ) {
				dur = u_dur;
				out = uchain;
			};
		});
		^out;	
	}
	
	dur { // get longest duration
		var uchain;
		uchain = this.getMaxDurChain;
		if( uchain.isNil ) { 
			^inf 
		} {
			^uchain.dur;
		};
	}

    /*
	* sets same duration for all units
	* clipFadeIn = true clips fadeIn
	* clipFadeIn = false clips fadeOut
	*/
	dur_ { |dur = inf, clipFadeIn = true|
		var currentDur, mul;
		currentDur = this.dur;
		if( (currentDur != inf) && { dur != inf } ) {
			mul = dur.max(1.0e-11) / currentDur.max(1.0e-11);
			uchains.do({ |uchain|
				if( uchain.dur != inf ) {
					uchain.dur_( uchain.dur * mul, clipFadeIn );
				};
			});
		};
	}
	
	duration { ^this.dur }
	duration_ { |x| this.dur_(x)}
	
	setGain { |gain = 0| // set the average gain of all units that have a u_gain arg
		var mean, add;
		mean = this.getGain;
		add = gain - mean;
		uchains.do({ |uchain|
			 uchain.setGain( uchain.getGain + add );
		});
		this.changed( \gain );		
	}
	
	getGain {
		var gains;
		gains = this.uchains.collect(_.getGain);
		if( gains.size > 0 ) { ^gains.mean } { ^0 };
	}
	
	
	start { |target, latency|
		^uchains.collect( _.start( target, latency ) );
	}
	
	free { uchains.do(_.free); }
	stop { uchains.do(_.stop); }
	
	release { |time|
		uchains.do( _.release( time ) );
	}

	prepare { |target, loadDef = true, action|
		action = MultiActionFunc( action );
	     uchains.do( _.prepare(target, loadDef, action.getAction ) );
	     action.getAction.value; // fire action at least once
	     ^target; // return array of actually prepared servers
	}

	prepareAndStart{ |target, loadDef = true|
		var task, cond;
		cond = Condition(false);
		task = fork { 
			var action;
			action = { cond.test = true; cond.signal };
			target = this.prepare( target, loadDef, action );
			cond.wait;
	       	this.start(target);
		};
	}
	
	waitTime { ^this.units.collect(_.waitTime).sum }
	
	prepareWaitAndStart { |target, loadDef = true|
		var task;
		task = fork { 
			this.prepare( target, loadDef );
			this.waitTime.wait; // doesn't care if prepare is done
	       	this.start(target);
	       	prepareTasks.remove(task);
		};
	}

	dispose { uchains.do( _.dispose ) }
	
	// indexing / access
		
	at { |index| ^units[ index ] }
		
	last { ^units.last }
	first { ^units.first }
	
	printOn { arg stream;
		stream << "a " << this.class.name << "(" <<* units.collect(_.defName)  <<")"
	}
	
	gui { |parent, bounds| ^UChainGUI( parent, bounds, this ) }
	
}