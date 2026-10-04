package com.gooroyeegar.pianostudio

import android.app.Activity
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.graphics.*
import android.view.*
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var piano: PianoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setNavigationBarColor(Color.rgb(9, 9, 11))
        piano = PianoView()
        setContentView(piano)
    }

    override fun onDestroy() {
        piano.releaseAudio()
        super.onDestroy()
    }

    inner class PianoView : View(this@MainActivity) {
        private val bg = Color.rgb(10,10,12)
        private val panel = Color.rgb(22,22,26)
        private val line = Color.rgb(48,48,54)
        private val text = Color.rgb(244,242,237)
        private val muted = Color.rgb(166,163,156)
        private val gold = Color.rgb(215,184,120)
        private val white = Color.rgb(248,245,237)
        private val whiteDown = Color.rgb(211,205,193)
        private val black = Color.rgb(18,18,21)
        private val blackDown = Color.rgb(82,67,42)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pressed = HashMap<Int,Int>()
        private val activeStreams = HashMap<Int,Int>()
        private val loaded = HashMap<String,Int>()
        private var loadedCount = 0
        private var audioReady = false
        private var sustain = false
        private var octave = 4
        private var volume = 0.88f
        private var keyWidth = 42f

        private var soundPool: SoundPool? = null
        private val sampleNotes = listOf("A0","C1","Ds1","Fs1","A1","C2","Ds2","Fs2","A2","C3","Ds3","Fs3","A3","C4","Ds4","Fs4","A4","C5","Ds5","Fs5","A5","C6","Ds6","Fs6","A6","C7","Ds7","Fs7","C8")
        private val midiNames = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        private val blackSemitones = setOf(1,3,6,8,10)
        private val startMidi = 21
        private val endMidi = 108

        init {
            setBackgroundColor(bg)
            // Audio is initialized only after the UI is visible and the user touches a key.
        }

        private fun noteName(m:Int) = midiNames[(m % 12 + 12) % 12] + (m / 12 - 1)

        private fun sampleMidi(name:String):Int {
            val letter = when(name[0]) {'C'->0;'D'->2;'E'->4;'F'->5;'G'->7;'A'->9;else->11}
            val sharp = if(name.getOrNull(1)=='s') 1 else 0
            val ds = if(name.getOrNull(1)=='s') 2 else 1
            return (name.substring(ds).toInt()+1)*12 + letter + sharp
        }

        private fun nearestSample(m:Int):Pair<Int,String>? {
            var best:String?=null; var dist=999
            for(n in sampleNotes) {
                val d=abs(sampleMidi(n)-m)
                if(d<dist && loaded.containsKey(n)){dist=d;best=n}
            }
            return best?.let{sampleMidi(it) to it}
        }

        private fun ensureAudio() {
            if (soundPool != null) return
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val pool = SoundPool.Builder().setAudioAttributes(attrs).setMaxStreams(32).build()
                pool.setOnLoadCompleteListener { _,_,status ->
                    if (status == 0) { loadedCount++; audioReady = loadedCount >= sampleNotes.size; invalidate() }
                }
                soundPool = pool
                Thread {
                    for (note in sampleNotes) {
                        try {
                            assets.openFd("samples/" + note + ".mp3").use { afd ->
                                val id = pool.load(afd,1)
                                synchronized(loaded) { loaded[note] = id }
                            }
                        } catch (_: Exception) {}
                    }
                }.start()
            } catch (_: Throwable) {
                audioReady = false
            }
        }

        private fun play(m:Int,v:Float,pid:Int) {
            ensureAudio()
            val pool = soundPool ?: return
            val s=nearestSample(m)?:return
            val id=loaded[s.second]?:return
            val rate=2.0.pow((m-s.first)/12.0).toFloat().coerceIn(0.5f,2f)
            val stream=pool.play(id,volume*v,volume*v,1,0,rate)
            if(stream!=0)activeStreams[pid]=stream
        }

        private fun stop(pid:Int) {
            val stream=activeStreams.remove(pid)?:return
            if(!sustain)soundPool?.stop(stream)
        }

        fun releaseAudio(){ try { soundPool?.release() } catch (_: Exception) {} }

        private fun visibleWhiteCount()=ceil(width.coerceAtLeast(1)/keyWidth).toInt().coerceAtLeast(8)

        private fun firstVisibleMidi():Int {
            var m=((octave+1)*12).coerceIn(startMidi,endMidi)
            while(m>startMidi && blackSemitones.contains(m%12))m--
            return m
        }

        private fun whites(start:Int,count:Int):List<Int>{
            val list=ArrayList<Int>();var m=start
            while(m<=endMidi && list.size<count+2){if(!blackSemitones.contains(m%12))list.add(m);m++}
            return list
        }

        private fun keyAt(x:Float,y:Float):Int? {
            val top=132f;val bottom=height-24f
            if(y<top||y>bottom)return null
            val ws=whites(firstVisibleMidi(),visibleWhiteCount())
            val wi=floor(x/keyWidth).toInt()
            if(wi !in ws.indices)return null
            val whiteMidi=ws[wi]
            val bw=keyWidth*0.62f;val bh=(bottom-top)*0.62f
            for(i in 0 until ws.size-1){
                val l=(i+1)*keyWidth-bw/2f
                if(x in l..(l+bw)&&y<=top+bh){
                    val candidate=ws[i]+1
                    if(blackSemitones.contains(candidate%12))return candidate
                }
            }
            return whiteMidi
        }

        private fun velocity(y:Float):Float {
            val top=132f;val bottom=height-24f
            return (0.35f+0.65f*(1f-((y-top)/(bottom-top)).coerceIn(0f,1f))).coerceIn(0.2f,1f)
        }

        override fun onDraw(c:Canvas){
            c.drawColor(bg)
            paint.color=panel;c.drawRoundRect(10f,8f,width-10f,78f,14f,14f,paint)
            textPaint.color=text;textPaint.textSize=18f;textPaint.typeface=Typeface.DEFAULT_BOLD
            c.drawText("PIANO STUDIO",24f,37f,textPaint)
            textPaint.color=if(audioReady)gold else muted;textPaint.textSize=11f;textPaint.typeface=Typeface.DEFAULT
            c.drawText(if(audioReady)"ACOUSTIC GRAND · MULTITOUCH READY" else "LOADING ACOUSTIC GRAND  "+loadedCount+"/"+sampleNotes.size,24f,58f,textPaint)

            button(c,"−",10f,86f,52f);button(c,"+",68f,86f,52f)
            button(c,"SUSTAIN "+if(sustain)"ON" else "OFF",128f,86f,128f)
            button(c,"VOL "+(volume*100).roundToInt()+"%",274f,86f,96f)
            textPaint.color=muted;textPaint.textSize=12f
            c.drawText("Octave "+octave,380f,111f,textPaint)
            c.drawText("A0–C8",width-75f,111f,textPaint)

            val top=132f;val bottom=height-24f
            paint.color=Color.rgb(38,31,25);c.drawRoundRect(8f,top-4f,width-8f,bottom+4f,10f,10f,paint)
            val ws=whites(firstVisibleMidi(),visibleWhiteCount())
            for((i,m) in ws.withIndex()){
                val l=i*keyWidth
                paint.color=if(pressed.containsValue(m))whiteDown else white
                c.drawRect(l,top,l+keyWidth-1f,bottom,paint)
                paint.color=Color.rgb(155,150,141);c.drawRect(l+keyWidth-1f,top,l+keyWidth,bottom,paint)
                if(m%12==0||m%12==5){textPaint.color=Color.rgb(100,96,90);textPaint.textSize=9f;c.drawText(noteName(m),l+4f,bottom-9f,textPaint)}
            }
            val bw=keyWidth*0.62f;val bh=(bottom-top)*0.62f
            for((i,m) in ws.withIndex()){
                val candidate=m+1
                if(blackSemitones.contains(candidate%12)){
                    val center=(i+1)*keyWidth;val l=center-bw/2f
                    paint.color=if(pressed.containsValue(candidate))blackDown else black
                    c.drawRoundRect(l,top,l+bw,top+bh,0f,0f,paint)
                }
            }
            if(!audioReady){
                textPaint.color=muted
                textPaint.textSize=10f
                c.drawText("Touch a key to load acoustic samples",18f,top+18f,textPaint)
            }
        }

        private fun button(c:Canvas,label:String,l:Float,t:Float,w:Float){
            paint.color=panel;c.drawRoundRect(l,t,l+w,t+40f,10f,10f,paint)
            paint.color=line;paint.style=Paint.Style.STROKE;c.drawRoundRect(l,t,l+w,t+40f,10f,10f,paint);paint.style=Paint.Style.FILL
            textPaint.color=text;textPaint.textSize=if(label.length>8)10f else 14f;c.drawText(label,l+10f,t+26f,textPaint)
        }

        private fun controlAt(x:Float,y:Float):Int {
            if(y !in 86f..126f)return -1
            return when{ x<62f->0;x<122f->1;x<268f->2;x<378f->3;else->-1 }
        }

        override fun onTouchEvent(e:MotionEvent):Boolean{
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN->{
                    val i=e.actionIndex;val id=e.getPointerId(i);val x=e.getX(i);val y=e.getY(i)
                    val control=controlAt(x,y)
                    if(control>=0){
                        when(control){0->octave=(octave-1).coerceAtLeast(1);1->octave=(octave+1).coerceAtMost(7);2->sustain=!sustain;3->{volume+=0.1f;if(volume>1f)volume=0.2f}}
                        invalidate();return true
                    }
                    val m=keyAt(x,y)
                    if(m!=null){pressed[id]=m;play(m,velocity(y),id);invalidate()}
                    return true
                }
                MotionEvent.ACTION_MOVE->{
                    for(i in 0 until e.pointerCount){
                        val id=e.getPointerId(i);val old=pressed[id]?:continue;val m=keyAt(e.getX(i),e.getY(i))?:continue
                        if(m!=old){stop(id);pressed[id]=m;play(m,velocity(e.getY(i)),id);invalidate()}
                    }
                    return true
                }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP,MotionEvent.ACTION_CANCEL->{
                    val i=if(e.actionMasked==MotionEvent.ACTION_POINTER_UP)e.actionIndex else 0
                    val id=e.getPointerId(i);stop(id);pressed.remove(id);invalidate();return true
                }
            }
            return true
        }
    }
}
