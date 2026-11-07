package com.loc.benimdunyam.feature.people.network.calculator

import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tamamen deterministik, sınırlandırılmış force-directed yerleşim.
 * Girdi yalnızca en fazla MAX_VISIBLE_PEOPLE düğüm içermelidir.
 */
object NetworkLayoutCalculator {
    fun layout(graph: PeopleNetworkGraph): PeopleNetworkGraph {
        val nodes = graph.nodes
        if (nodes.isEmpty()) return graph
        val count = nodes.size
        val x = FloatArray(count)
        val y = FloatArray(count)
        val goldenAngle = (PI * (3.0 - sqrt(5.0))).toFloat()

        for (i in nodes.indices) {
            val angle = i * goldenAngle
            val radius = 135f + 65f * sqrt(i.toFloat())
            x[i] = cos(angle) * radius
            y[i] = sin(angle) * radius
        }
        val indexById = nodes.mapIndexed { index, node -> node.id to index }.toMap()
        val links = graph.edges.mapNotNull { edge ->
            val a = indexById[edge.firstPersonId]
            val b = indexById[edge.secondPersonId]
            if (a == null || b == null) null else Triple(a, b, edge.sharedMemoryCount)
        }

        repeat(90) { iteration ->
            val fx = FloatArray(count)
            val fy = FloatArray(count)
            for (i in 0 until count) {
                for (j in (i + 1) until count) {
                    val dx = x[i] - x[j]
                    val dy = y[i] - y[j]
                    val length = max(1f, sqrt(dx * dx + dy * dy))
                    // Düğümlerin üzerinde metin ve avatar bulunuyor.
                    val force = min(25f, 170000f / (length * length))
                    val vx = dx / length * force
                    val vy = dy / length * force
                    fx[i] += vx
                    fy[i] += vy
                    fx[j] -= vx
                    fy[j] -= vy
                }
            }
            for ((a, b, strength) in links) {
                val dx = x[b] - x[a]
                val dy = y[b] - y[a]
                val length = max(1f, sqrt(dx * dx + dy * dy))
                val desired = max(165f, 265f - (min(strength, 12) * 8f))
                val force = ((length - desired) * 0.022f).coerceIn(-15f, 15f)
                val vx = dx / length * force
                val vy = dy / length * force
                fx[a] += vx
                fy[a] += vy
                fx[b] -= vx
                fy[b] -= vy
            }
            val damping = 0.7f * (1f - iteration / 125f)
            for (i in 0 until count) {
                fx[i] -= x[i] * 0.007f
                fy[i] -= y[i] * 0.007f
                x[i] += fx[i].coerceIn(-24f, 24f) * damping
                y[i] += fy[i].coerceIn(-24f, 24f) * damping
            }
        }
        return graph.copy(nodes = nodes.mapIndexed { i, node -> node.copy(x = x[i], y = y[i]) })
    }
}
