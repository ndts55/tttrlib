package org.ndts.tttrlib

import java.io.Serializable

const val DIM = 3
const val DD = DIM * DIM

private val lineIndices: Array<Array<Int>> =
    arrayOf(*(0 until DIM).map { i -> (0 until DIM).map { j -> i * DIM + j }.toTypedArray<Int>() }
        .toTypedArray<Array<Int>>(),
        *(0 until DIM).map { i -> (0 until DIM).map { j -> j * DIM + i }.toTypedArray<Int>() }
            .toTypedArray<Array<Int>>(),
        (0 until DIM).map { it * DIM + it }.toTypedArray(),
        (0 until DIM).map { (DIM - it - 1) * DIM + it }.toTypedArray()
    )

private fun <T> checkLines(arr: Array<T>, desired: T): Boolean = getMatchingLine(
    arr, desired
) != null

private fun <T> checkDraw(arr: Array<T>, none: T): Boolean = arr.none { it == none }

private fun <T> getMatchingLine(arr: Array<T>, desired: T): Array<Int>? =
    lineIndices.find { it.all { idx -> arr[idx] == desired } }
//endregion

// region State
enum class TileState {
    Cross, Circle, None
}

enum class InnerBoardResult {
    Cross, Circle, None, Draw;
}

enum class OuterBoardResult {
    Cross, Circle, None, Draw;
}

data class InnerBoardState(
    val result: InnerBoardResult, val tiles: Array<TileState>, val enabled: Boolean, val formsWinLine: Boolean
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InnerBoardState

        if (result != other.result) return false
        if (!tiles.contentEquals(other.tiles)) return false
        if (enabled != other.enabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = result.hashCode()
        result1 = 31 * result1 + tiles.contentHashCode()
        result1 = 31 * result1 + enabled.hashCode()
        return result1
    }
}

data class OuterBoardState(
    val result: OuterBoardResult, val innerBoards: Array<InnerBoardState>
) : Serializable {
    fun apply(event: PlayEvent): OuterBoardState {
        // Is it even valid to have clicked on the specified tile?
        if (result != OuterBoardResult.None || !innerBoards[event.boardId].enabled || innerBoards[event.boardId].result != InnerBoardResult.None || innerBoards[event.boardId].tiles[event.tileId] != TileState.None) return this
        val nextTiles = innerBoards[event.boardId].tiles.clone()
        nextTiles[event.tileId] = event.player.ts()

        val nextInnerBoardResult = when {
            checkLines(nextTiles, event.player.ts()) -> event.player.ibr()
            checkDraw(
                nextTiles, TileState.None
            ) -> InnerBoardResult.Draw

            else -> InnerBoardResult.None
        }

        val (nextOuterBoardResult, line) = innerBoards.mapIndexed { boardId, innerBoardState ->
            if (boardId == event.boardId) nextInnerBoardResult else innerBoardState.result
        }.toTypedArray().let { arr ->
            val line = getMatchingLine(arr, event.player.ibr())
            when {
                line != null -> Pair(event.player.obr(), line)
                checkDraw(
                    arr, InnerBoardResult.None
                ) -> Pair(OuterBoardResult.Draw, null)

                else -> Pair(OuterBoardResult.None, null)
            }
        }

        val isEnabled = when (nextOuterBoardResult) {
            OuterBoardResult.Cross, OuterBoardResult.Circle, OuterBoardResult.Draw -> { _: Int -> false }

            OuterBoardResult.None -> {
                val relevantBoardResult =
                    if (event.boardId == event.tileId) nextInnerBoardResult else innerBoards[event.tileId].result
                val nextEnabledBoardId = if (relevantBoardResult == InnerBoardResult.None) event.tileId else null
                { bid: Int -> nextEnabledBoardId == null || bid == nextEnabledBoardId }
            }
        }
        val isFormsWinLine = when (nextOuterBoardResult) {
            OuterBoardResult.Cross, OuterBoardResult.Circle -> { bid: Int -> line?.contains(bid) ?: false }

            else -> { _ -> false }
        }

        val changedInnerBoard = InnerBoardState(
            nextInnerBoardResult, nextTiles, isEnabled(event.boardId), isFormsWinLine(event.boardId)
        )
        val nextInnerBoards = innerBoards.mapIndexed { boardId, innerBoardState ->
            if (boardId == event.boardId) changedInnerBoard else innerBoardState.copy(
                enabled = isEnabled(boardId), formsWinLine = isFormsWinLine(boardId)
            )
        }.toTypedArray()
        return this.copy(result = nextOuterBoardResult, innerBoards = nextInnerBoards)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OuterBoardState

        if (result != other.result) return false
        if (!innerBoards.contentEquals(other.innerBoards)) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = result.hashCode()
        result1 = 31 * result1 + innerBoards.contentHashCode()
        return result1
    }
}

@Suppress("unused")
data class GameState(
    val player: Player = Player.Cross, val outerBoardState: OuterBoardState = OuterBoardState(
        OuterBoardResult.None, (0 until DD).map {
            InnerBoardState(result = InnerBoardResult.None,
                tiles = (0 until DD).map { TileState.None }.toTypedArray(),
                enabled = true,
                formsWinLine = false)
        }.toTypedArray()
    )
) : Serializable {
    fun apply(event: PlayEvent): GameState {
        if (event.player != player) return this
        val nextOuterBoardState = outerBoardState.apply(event)
        return if (nextOuterBoardState === outerBoardState) this else this.copy(
            player = when (nextOuterBoardState.result) {
                OuterBoardResult.None -> player.next()
                else -> player
            }, outerBoardState = nextOuterBoardState
        )
    }
}
// endregion

// region Events
enum class Player {
    Cross, Circle;

    fun next(): Player = when (this) {
        Cross -> Circle
        Circle -> Cross
    }

    fun ts(): TileState = when (this) {
        Cross -> TileState.Cross
        Circle -> TileState.Circle
    }

    fun ibr(): InnerBoardResult = when (this) {
        Cross -> InnerBoardResult.Cross
        Circle -> InnerBoardResult.Circle
    }

    fun obr(): OuterBoardResult = when (this) {
        Cross -> OuterBoardResult.Cross
        Circle -> OuterBoardResult.Circle
    }
}

data class PlayEvent(val player: Player, val boardId: Int, val tileId: Int) : Serializable
// endregion
