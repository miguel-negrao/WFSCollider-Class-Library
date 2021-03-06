// if using supernova (!!!!) run this first:

Server.program = "cd % && exec ./supernova -U plugins".format(String.scDir.quote);

//make sure there are no servers running prior to this
//killall scsynth

(
~soptions = ServerOptions()
.memSize_(8192*12)
.numAudioBusChannels_(150)
.numOutputBusChannels_(2)
.numInputBusChannels_(30)
.numWireBufs_(2048)
.sampleRate_( 44100 )
.blockAllocClass_( ContiguousBlockAllocator )
.device_("JackRouter");

~slaves = [
        Server(\slave1,NetAddr("localhost", 57456),~soptions),
        Server(\slave2,NetAddr("localhost", 57458),~soptions)
];

~master = Server(\master,NetAddr("localhost", 57368),~soptions);

(~slaves++[~master]).do(_.makeWindow);

SyncCenter.inBus = 0;
SyncCenter.outBus = 0;
SyncCenter.addAll(~slaves);
SyncCenter.master_(~master);


{
        ~master.boot;
        2.wait;
        ~slaves[0].boot;
        2.wait;
        ~slaves[1].boot;
        
        while({ ~master.serverBooting || ~slaves[0].serverBooting || ~slaves[1].serverBooting }) {
                1.wait;
        };
        "servers booted".postln;
        
        (2.collect{ |i|
                "jack_disconnect  system:capture_"++(i+1)++" scsynth:in"++(i+1)++" && "++
                "jack_disconnect  system:capture_"++(i+1)++" scsynth-01:in"++(i+1)++" && "++
                "jack_disconnect  system:capture_"++(i+1)++" scsynth-02:in"++(i+1)++" && "
        }.reduce('++') ++ "jack_connect scsynth:out1 scsynth-01:in1 && jack_connect scsynth:out1 scsynth-02:in1").runInTerminal;
        
        SyncCenter.sendDefs;
        

}.fork;

)

SyncCenter.remoteSync

~slaves.do({ arg server;
        SynthDef("sine", { arg out=0, freq=110.0, amp=0.1;
                OffsetOut.ar(out, SinOsc.ar(freq, 0, amp).dup);
        }).send(server);
});


OSCBundle.new.add([ "/s_new", "sine", 2000, 1, 1, 'freq', 1000, 'amp', 0.25 ]).syncedSend(~slaves[0],1);
OSCBundle.new.add([ "/s_new", "sine", 2000, 1, 1, 'freq', 1000, 'amp', -0.25 ]).syncedSend(~slaves[1],2);
//kill slave1 or slave2 and you will hear sine wave again.

//this number should be the same between syncs
SyncCenter.serverCounts.collect{ |x| x.value - SyncCenter.current.localCount }
