package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * METEOR：星がまたたき、たまにだけ流れ星が流れる。
 * 流れ星は「正午から翌日の正午」を1日として、設定回数の約8割を 20時〜翌6時に流す。
 * プレビューでは、頻度を上げて見せる。
 */
class MeteorToy(ctx: ToyContext) : Toy(ctx) {
    private class Star(val x: Int, val y: Int, val ph: Double, val sp: Double)
    private class Meteor(var x: Double, var y: Double, val dx: Double, val dy: Double) {
        val trail = ArrayDeque<IntArray>()
    }

    private val stars = ArrayList<Star>()
    private var m: Meteor? = null
    private var next = 0

    /** 今の「1日」（正午〜翌日正午）の流れ星の予定（エポックミリ秒、昇順）。 */
    private var schedule = LongArray(0)
    private var dayStart = Long.MIN_VALUE
    private var dayEnd = Long.MIN_VALUE
    private var lastCheck = 0L

    val scheduled: LongArray get() = schedule.copyOf()

    override val stepsPerSecond: Double get() = 10.0

    override fun init() {
        val many = params.int("stars") == 1
        val count = if (geo.big) (if (many) 48 else 20) else (if (many) 16 else 7)
        val cells = (0 until geo.size).filter { geo.inside(it) }.shuffled(random).take(count)
        stars.clear()
        for (i in cells) stars.add(Star(i % n, i / n, random.nextDouble() * 6, 0.6 + random.nextDouble() * 1.8))
        m = null
        next = 40 + rnd(40)
        dayEnd = Long.MIN_VALUE
        lastCheck = inputs.epochMillis
    }

    /** 流れ星を呼ぶ（試し操作）。流れている間は何もしない。 */
    override fun act() {
        if (m != null) return
        val dir = if (rnd(2) == 1) 1 else -1
        val sp = if (geo.big) 1.3 else 0.9
        m = Meteor(if (dir > 0) 0.0 else (n - 1).toDouble(), (1 + rnd((n / 3.0).roundToInt())).toDouble(), dir * sp, sp / 2)
    }

    override fun step() {
        if (demo) {
            if (m == null && --next <= 0) {
                act()
                next = ((150 + rnd(150)) * 3.0 / (params.int("per") + 2)).roundToInt()
            }
        } else {
            checkSchedule()
        }
        m?.let { mm ->
            mm.trail.addFirst(intArrayOf(jr(mm.x), jr(mm.y)))
            if (mm.trail.size > tailLength) mm.trail.removeLast()
            mm.x += mm.dx
            mm.y += mm.dy
            if (mm.x < -8 || mm.x > n + 8 || mm.y > n + 4) m = null
        }
    }

    private val tailLength: Int get() = if (geo.big) 8 else 5

    private fun checkSchedule() {
        val now = inputs.epochMillis
        if (now >= dayEnd || now < dayStart) planDay(now)
        // 前回見てから今までの間に予定があれば流す。止まっていて大きく過ぎた予定は捨てる。
        for (t in schedule) {
            if (t in (lastCheck + 1)..now && now - t < LATE_LIMIT_MS) {
                act()
                break
            }
        }
        lastCheck = now
    }

    /** now を含む「1日」の予定を作る。 */
    private fun planDay(now: Long) {
        val zone = inputs.zone
        val z = inputs.zoned()
        val startDate: LocalDate = if (z.toLocalTime() < LocalTime.NOON) z.toLocalDate().minusDays(1) else z.toLocalDate()
        val start = ZonedDateTime.of(startDate, LocalTime.NOON, zone)
        dayStart = start.toInstant().toEpochMilli()
        dayEnd = start.plusDays(1).toInstant().toEpochMilli()
        val nightStart = start.withHour(20).toInstant().toEpochMilli()
        val nightEnd = start.plusDays(1).withHour(6).toInstant().toEpochMilli()

        val per = params.int("per")
        val nightCount = (per * NIGHT_SHARE).roundToInt()
        val times = ArrayList<Long>()
        repeat(nightCount) { times.add(nightStart + (random.nextDouble() * (nightEnd - nightStart)).toLong()) }
        // 昼の分は、正午〜20時と6時〜正午をひと続きとみなして均等に置く
        val dayA = nightStart - dayStart
        val dayB = dayEnd - nightEnd
        repeat(per - nightCount) {
            val r = (random.nextDouble() * (dayA + dayB)).toLong()
            times.add(if (r < dayA) dayStart + r else nightEnd + (r - dayA))
        }
        schedule = times.sorted().toLongArray()
    }

    override fun draw(out: FloatArray) {
        for (st in stars) {
            val v = 0.5 + 0.5 * sin(time * st.sp + st.ph)
            // 薄い帯の中（暗い陣地の明るさ〜その2倍）でゆっくりまたたく（LedMapper）
            out[geo.idx(st.x, st.y)] = (0.12 + 0.23 * v * v).toFloat()
        }
        m?.let { mm ->
            val tl = tailLength
            mm.trail.forEachIndexed { k, c -> put(out, c[0], c[1], 0.75 * (1 - k.toDouble() / tl)) }
            put(out, jr(mm.x), jr(mm.y), 1.0)
        }
    }

    private companion object {
        const val NIGHT_SHARE = 0.8
        const val LATE_LIMIT_MS = 60_000L
    }
}
