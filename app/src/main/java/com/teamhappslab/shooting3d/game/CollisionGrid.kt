package com.teamhappslab.shooting3d.game

/**
 * 2D均一グリッド当たり判定（counting sortで毎フレーム再構築）。
 * 自機周辺9セルのみ照会。距離比較はsqrt不使用（二乗比較）。
 */
class CollisionGrid(
    private val maxItems: Int,
    private val minX: Float, private val maxX: Float,
    private val minZ: Float, private val maxZ: Float,
    private val cellSize: Float
) {
    private val cols = (((maxX - minX) / cellSize).toInt() + 1)
    private val rows = (((maxZ - minZ) / cellSize).toInt() + 1)
    private val nCells = cols * rows
    private val invCell = 1f / cellSize

    private val cellStart = IntArray(nCells + 1)
    private val cellCount = IntArray(nCells)
    private val itemIdx = IntArray(maxItems)
    private val itemCell = IntArray(maxItems)
    private var built = 0

    private fun cellOf(x: Float, z: Float): Int {
        var cx = ((x - minX) * invCell).toInt()
        var cz = ((z - minZ) * invCell).toInt()
        if (cx < 0) cx = 0 else if (cx >= cols) cx = cols - 1
        if (cz < 0) cz = 0 else if (cz >= rows) cz = rows - 1
        return cz * cols + cx
    }

    /** counting sortで再構築 */
    fun build(posX: FloatArray, posZ: FloatArray, count: Int) {
        built = if (count > maxItems) maxItems else count
        java.util.Arrays.fill(cellCount, 0)
        var i = 0
        while (i < built) {
            val c = cellOf(posX[i], posZ[i])
            itemCell[i] = c
            cellCount[c]++
            i++
        }
        var sum = 0
        var c = 0
        while (c < nCells) {
            cellStart[c] = sum
            sum += cellCount[c]
            cellCount[c] = cellStart[c]  // 挿入カーソルとして再利用
            c++
        }
        cellStart[nCells] = sum
        i = 0
        while (i < built) {
            val cc = itemCell[i]
            itemIdx[cellCount[cc]] = i
            cellCount[cc]++
            i++
        }
    }

    /**
     * (x,z)周辺9セル内の弾インデックスをoutに収集して個数を返す。
     */
    fun queryNeighbors(x: Float, z: Float, out: IntArray): Int {
        var cx = ((x - minX) * invCell).toInt()
        var cz = ((z - minZ) * invCell).toInt()
        if (cx < 0) cx = 0 else if (cx >= cols) cx = cols - 1
        if (cz < 0) cz = 0 else if (cz >= rows) cz = rows - 1
        var n = 0
        var dz = -1
        while (dz <= 1) {
            val zz = cz + dz
            if (zz in 0 until rows) {
                var dx = -1
                while (dx <= 1) {
                    val xx = cx + dx
                    if (xx in 0 until cols) {
                        val cell = zz * cols + xx
                        var k = cellStart[cell]
                        val end = cellStart[cell + 1]
                        while (k < end) {
                            if (n < out.size) {
                                out[n] = itemIdx[k]
                                n++
                            }
                            k++
                        }
                    }
                    dx++
                }
            }
            dz++
        }
        return n
    }
}
