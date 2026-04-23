package models;

import java.util.ArrayList;
import java.util.List;

// Full Logic for Checkers game
public class CheckersLogic {

    // constants
    public static final int SIZE = 8;
    public static final int EMPTY = 0;
    public static final int RED = 1;
    public static final int RED_KING = 2;
    public static final int BLACK = 3;
    public static final int BLACK_KING = 4;

    // state variables
    private final int[][] board = new int[SIZE][SIZE];
    private boolean redTurn = false;
    private boolean gameOver = false;
    private String winner = null;

    // For multi-jump sequence
    private int jumpingRow = -1;
    private int jumpingCol = -1;

    // Constructor
    public CheckersLogic() {
        initBoard();
    }

    // copy constructor for the entire game state
    public CheckersLogic(CheckersLogic other) {
        for (int r = 0; r < SIZE; r++)
            System.arraycopy(other.board[r], 0, this.board[r], 0, SIZE);
        this.redTurn = other.redTurn;
        this.gameOver = other.gameOver;
        this.winner = other.winner;
        this.jumpingRow = other.jumpingRow;
        this.jumpingCol = other.jumpingCol;
    }

    // init board with black and red pieces
    private void initBoard() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) { // dark square
                    if (r < 3)
                        board[r][c] = BLACK;
                    else if (r > 4)
                        board[r][c] = RED;
                    else
                        board[r][c] = EMPTY;
                } else {
                    board[r][c] = EMPTY;
                }
            }
        }
    }

    // accessors
    public int[][] getBoard() {
        int[][] copy = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++)
            System.arraycopy(board[r], 0, copy[r], 0, SIZE);
        return copy;
    }

    public boolean isRedTurn() {
        return redTurn;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinner() {
        return winner;
    }

    public boolean isMidJump() {
        return jumpingRow >= 0;
    }

    public int getJumpingRow() {
        return jumpingRow;
    }

    public int getJumpingCol() {
        return jumpingCol;
    }

    // count pieces red and black still has
    public int[] getPieceCounts() {
        int red = 0, black = 0;
        for (int[] row : board)
            for (int cell : row) {
                if (isRed(cell))
                    red++;
                if (isBlack(cell))
                    black++;
            }
        return new int[] { red, black };
    }

    // piece helpers
    private static boolean isRed(int p) {
        return p == RED || p == RED_KING;
    }

    private static boolean isBlack(int p) {
        return p == BLACK || p == BLACK_KING;
    }

    private static boolean isKing(int p) {
        return p == RED_KING || p == BLACK_KING;
    }

    private boolean isCurrentPlayer(int p) {
        return redTurn ? isRed(p) : isBlack(p);
    }

    private boolean isOpponent(int p) {
        return redTurn ? isBlack(p) : isRed(p);
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    /**
     * Returns all squares [toRow, toCol] the piece at (row,col) may legally
     * move to this turn, accounting for must capture and multi-jump rules.
     * Returns an empty list if the piece cannot move.
     */
    public List<int[]> getLegalMoves(int row, int col) {
        List<int[]> result = new ArrayList<>();
        if (!isCurrentPlayer(board[row][col]))
            return result;

        // Mid-jump: only the jumping piece can continue, and only with captures
        if (jumpingRow >= 0) {
            if (jumpingRow != row || jumpingCol != col)
                return result;
            return capturesFrom(row, col);
        }

        // must capture: if any piece can capture, return only captures for this piece
        if (anyCapture()) {
            return capturesFrom(row, col); // may be empty for this piece
        }

        // return simple diagonal moves
        return simpleMoves(row, col);
    }

    /**
     * Returns all [row,col] positions of current player's pieces that have
     * at least one legal capture this turn.
     */
    public List<int[]> getPiecesWithCaptures() {
        List<int[]> result = new ArrayList<>();
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (isCurrentPlayer(board[r][c]) && !capturesFrom(r, c).isEmpty())
                    result.add(new int[] { r, c });
        return result;
    }

    /** True when the current player has at least one mandatory capture. */
    public boolean anyCapture() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (isCurrentPlayer(board[r][c]) && !capturesFrom(r, c).isEmpty())
                    return true;
        return false;
    }

    // ── Internal move generators ──────────────────────────────────────────────
    private List<int[]> simpleMoves(int row, int col) {
        List<int[]> result = new ArrayList<>();
        int piece = board[row][col];
        for (int dr : directions(piece)) {
            for (int dc : new int[] { -1, 1 }) {
                int nr = row + dr, nc = col + dc;
                if (inBounds(nr, nc) && board[nr][nc] == EMPTY)
                    result.add(new int[] { nr, nc });
            }
        }
        return result;
    }

    /** Capture destinations from (row,col). Does NOT check whose turn it is. */
    private List<int[]> capturesFrom(int row, int col) {
        List<int[]> result = new ArrayList<>();
        int piece = board[row][col];
        for (int dr : directions(piece)) {
            for (int dc : new int[] { -1, 1 }) {
                int mr = row + dr, mc = col + dc; // potential victim
                int nr = row + 2 * dr, nc = col + 2 * dc; // landing square
                if (inBounds(nr, nc) && isOpponent(board[mr][mc]) && board[nr][nc] == EMPTY) {
                    result.add(new int[] { nr, nc });
                }
            }
        }
        return result;
    }

    /** Forward row direction(s) for a piece. Kings return both. */
    private static int[] directions(int piece) {
        if (piece == RED)
            return new int[] { -1 };
        if (piece == BLACK)
            return new int[] { 1 };
        return new int[] { -1, 1 }; // king
    }

    // ── Move execution ────────────────────────────────────────────────────────

    /**
     * Attempts to execute the move from (fromRow,fromCol) to (toRow,toCol).
     * 
     * @return true if the move was legal and executed successfully.
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        // Validate
        boolean valid = getLegalMoves(fromRow, fromCol)
                .stream().anyMatch(m -> m[0] == toRow && m[1] == toCol);
        if (!valid)
            return false;

        int piece = board[fromRow][fromCol];
        boolean wasKingBefore = isKing(piece);
        boolean wasCapture = (Math.abs(toRow - fromRow) == 2);

        // Move piece
        board[fromRow][fromCol] = EMPTY;
        board[toRow][toCol] = piece;

        // Remove captured piece
        if (wasCapture) {
            int mr = (fromRow + toRow) / 2;
            int mc = (fromCol + toCol) / 2;
            board[mr][mc] = EMPTY;
        }

        // Promotion
        boolean justPromoted = false;
        if (isRed(piece) && toRow == 0 && !wasKingBefore) {
            board[toRow][toCol] = RED_KING;
            justPromoted = true;
        } else if (isBlack(piece) && toRow == SIZE - 1 && !wasKingBefore) {
            board[toRow][toCol] = BLACK_KING;
            justPromoted = true;
        }

        // Decide whether the turn continues (multi-jump)
        boolean canContinue = wasCapture
                && !justPromoted
                && !capturesFrom(toRow, toCol).isEmpty();

        if (canContinue) {
            jumpingRow = toRow;
            jumpingCol = toCol;
            // Turn does NOT switch — the same player must jump again
        } else {
            jumpingRow = -1;
            jumpingCol = -1;
            redTurn = !redTurn;
            checkGameOver();
        }

        return true;
    }

    // ── Game-over detection ───────────────────────────────────────────────────

    private void checkGameOver() {
        // Piece count wins
        int[] counts = getPieceCounts();
        if (counts[0] == 0) {
            winner = "BLACK";
            gameOver = true;
            return;
        }
        if (counts[1] == 0) {
            winner = "RED";
            gameOver = true;
            return;
        }

        // Can the current player move at all?
        if (!hasAnyMove(redTurn)) {
            // Current player blocked — check if opponent is also blocked
            if (!hasAnyMove(!redTurn)) {
                winner = "DRAW";
            } else {
                // Blocked player loses; the OTHER colour wins
                winner = redTurn ? "BLACK" : "RED";
            }
            gameOver = true;
        }
    }

    /** True if the given side (red=true) has at least one legal move. */
    private boolean hasAnyMove(boolean checkRed) {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++) {
                int p = board[r][c];
                boolean belongs = checkRed ? isRed(p) : isBlack(p);
                if (belongs) {
                    if (!simpleMoves(r, c).isEmpty() || !capturesFrom(r, c).isEmpty())
                        return true;
                }
            }
        return false;
    }
}
