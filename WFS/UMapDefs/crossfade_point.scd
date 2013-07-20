/*
\crossfade_point

Creates an UMap for crossfading between two points via linear interpolation. The points can also be UMaps.

a: the first point
b: the second point
crossfade: (0-1) the crossfading position (a to b)
lag: a lag time, applied to the crossfade position

--
this is an UMapDef definition file
part of the WFSCollider Class Library default UMapDefs set
*/

UMapDef( \crossfade_point, { |
		a = #[0.0, 0.0],
		b = #[10.0,10.0],
		crossfade = 0.5, 
		lag = 0.0|
	crossfade = LPFLag.kr( crossfade, lag );
	UMapOut.kr( (a * (1-crossfade)) + (b * crossfade) );
})
	.category_( 'point' )
	.canUseUMapFunc_({ |unit, key, umapdef|
		unit.getSpec( key ).isKindOf( PointSpec );
	})
	.setSpec( \a, WFSPointSpec() )
	.setSpec( \b, WFSPointSpec() );