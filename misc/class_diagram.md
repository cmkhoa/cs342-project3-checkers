### **Checkers Project Structure (JSON Style)**

```json
{
  "Server": {
    "attributes": {
      "games": "List<Game>",
      "users": "Map<string, User>",
      "matchingQueue": "List<User>"
    },
    "methods": ["registerUser(): bool"]
  },
  "Client": {
    "attributes": {
      "serverAddress": "string",
      "username": "string"
    },
    "methods": ["connect(): void", "disconnect(): void", "sendMove(m: Move)"]
  },
  "User": {
    "attributes": {
      "username": "string",
      "password": "string",
      "wins": "int",
      "loss": "int",
      "online": "bool"
    },
    "methods": ["addWon(): void", "addLoss(): void", "setOnline(bool): void"]
  },
  "Game": {
    "attributes": {
      "board": "Board",
      "player1": "User",
      "player2": "User",
      "user1Turn": "bool",
      "isDone": "bool",
      "chatLog": "List<Messages>"
    },
    "methods": ["startGame(): bool", "checkWin(): User", "checkDraw(): bool"]
  },
  "Board": {
    "attributes": {
      "grid": "Piece[8][8]",
      "moveLog": "List<Move>"
    },
    "methods": ["initBoard(): void", "getPiece(r, c: int): Piece", "movePiece(m: Move): bool"]
  },
  "Piece": {
    "attributes": {
      "color": "string",
      "isKing": "bool",
      "taken": "bool"
    },
    "methods": ["promote(): void"]
  },
  "Messages": {
    "attributes": {
      "sender": "User",
      "content": "string",
      "timestamp": "string",
      "gameID": "string"
    },
    "methods": ["getSender(): string", "getContent(): string"]
  }
}