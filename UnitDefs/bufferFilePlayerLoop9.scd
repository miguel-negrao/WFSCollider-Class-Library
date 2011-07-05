Udef( \bufferFilePlayerLoop9, { | bufnum, rate = 1.0, level = 1.0, i_fadeInTime = 0.1,
		i_fadeOutTime = 0.1, i_startOffset = 0, i_eventDuration, loopTime| 
		var env = EnvGen.kr( Env.new([0,1,1,0], 
			[ i_fadeInTime,(i_eventDuration - (i_fadeInTime + i_fadeOutTime)).max(0),i_fadeOutTime])
		, doneAction:14);
		UOut.ar( 0, level * env * PlayBufCF.ar(9, bufnum, rate, Impulse.kr(rate*loopTime.reciprocal), loop:1) );
	})