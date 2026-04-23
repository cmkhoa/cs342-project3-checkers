package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimax-based AI player.
 *
 * The AI always plays as BLACK (player 2).
 * Reference: https://github.com/Gualor/checkers-minimax
 */
public class CheckersAI {

    private final int maxDepth;

    public CheckersAI() {
        this(3);
    }

    public CheckersAI(int depth) {
        this.maxDepth = depth;
    }

    /**
     * Return the best move for the AI
     *
     * If the AI is mid-jump it returns only the continuation move.
     * Returns null if no move is available.
     */
    public int[] bestMove(CheckersLogic game) {
        // create a board to simulate the player's possible moves
        int[][] board = game.getBoard();
        boolean redTurn = game.isRedTurn();

        List<int[]> allMoves = getAllMoves(game, board, redTurn);
        if (allMoves.isEmpty())
            return null;

        int bestScore = Integer.MIN_VALUE;
        int[] best = null;

        // loop through all possible moves
        for (int[] move : allMoves) {
            CheckersLogic sim = cloneGame(game);
            sim.makeMove(move[0], move[1], move[2], move[3]);

            // If the move leads to a multi-jump, complete it greedily
            completeMultiJump(sim);

            int score = minimax(sim, maxDepth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                    sim.isRedTurn() == redTurn);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    // ── Minimax with alpha-beta pruning ────────────────────────────────────

    private int minimax(CheckersLogic game, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || game.isGameOver()) {
            return evaluate(game);
        }

        int[][] board = game.getBoard();
        boolean redTurn = game.isRedTurn();
        List<int[]> moves = getAllMoves(game, board, redTurn);

        if (moves.isEmpty()) {
            return evaluate(game);
        }

        if (maximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : moves) {
                CheckersLogic sim = cloneGame(game);
                sim.makeMove(move[0], move[1], move[2], move[3]);
                completeMultiJump(sim);
                int eval = minimax(sim, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha)
                    break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : moves) {
                CheckersLogic sim = cloneGame(game);
                sim.makeMove(move[0], move[1], move[2], move[3]);
                completeMultiJump(sim);
                int eval = minimax(sim, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha)
                    break;
            }
            return minEval;
        }
    }

    // ── Evaluation function ──────────────────────────────────────────────
    // AI plays BLACK. Positive = good for BLACK, negative = good for RED.

    private int evaluate(CheckersLogic game) {
        if (game.isGameOver()) {
            String w = game.getWinner();
            if ("BLACK".equals(w))
                return 1000;
            if ("RED".equals(w))
                return -1000;
            return 0; // draw
        }

        int[][] board = game.getBoard();
        int score = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece == CheckersLogic.EMPTY)
                    continue;

                boolean isBlack = (piece == CheckersLogic.BLACK || piece == CheckersLogic.BLACK_KING);
                boolean isKing = (piece == CheckersLogic.RED_KING || piece == CheckersLogic.BLACK_KING);
                int sign = isBlack ? 1 : -1;

                // Piece value: king=100, regular=20
                score += sign * (isKing ? 100 : 20);

                // Positional: how far advanced toward promotion
                // BLACK advances toward row 7, RED advances toward row 0
                double advancement;
                if (isBlack) {
                    advancement = (double) r / 7.0; // 0 at top, 1 at bottom (promotion)
                } else {
                    advancement = (double) (7 - r) / 7.0; // 0 at bottom, 1 at top (promotion)
                }
                score += sign * (int) (advancement * 10);

                // Positional: prefer edges (harder to capture)
                double centerDist = Math.abs(c - 3.5);
                double edgeBonus = (1.0 - (0.5 / Math.max(centerDist, 0.01))) * 5;
                if (centerDist > 0.5) {
                    score += sign * (int) edgeBonus;
                }
            }
        }

        return score;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    // Get all possible {fromR, fromC, toR, toC} moves for the current player.
    private List<int[]> getAllMoves(CheckersLogic game, int[][] board, boolean redTurn) {
        List<int[]> moves = new ArrayList<>();

        // If mid-jump, only that piece can move
        if (game.isMidJump()) {
            int jr = game.getJumpingRow(), jc = game.getJumpingCol();
            for (int[] dest : game.getLegalMoves(jr, jc)) {
                moves.add(new int[] { jr, jc, dest[0], dest[1] });
            }
            return moves;
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int p = board[r][c];
                boolean belongs = redTurn
                        ? (p == CheckersLogic.RED || p == CheckersLogic.RED_KING)
                        : (p == CheckersLogic.BLACK || p == CheckersLogic.BLACK_KING);
                if (!belongs)
                    continue;
                for (int[] dest : game.getLegalMoves(r, c)) {
                    moves.add(new int[] { r, c, dest[0], dest[1] });
                }
            }
        }
        return moves;
    }

    // Complete any remaining multi-jump sequence greedily (pick first available).
    private void completeMultiJump(CheckersLogic sim) {
        while (sim.isMidJump()) {
            int jr = sim.getJumpingRow(), jc = sim.getJumpingCol();
            List<int[]> captures = sim.getLegalMoves(jr, jc);
            if (captures.isEmpty())
                break;
            sim.makeMove(jr, jc, captures.get(0)[0], captures.get(0)[1]);
        }
    }

    // Deep-clone a CheckersLogic by replaying all state.
    private CheckersLogic cloneGame(CheckersLogic original) {
        return new CheckersLogic(original);
    }
}
