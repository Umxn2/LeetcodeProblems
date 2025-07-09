
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class Problems {
  private static final String DB_URL = "jdbc:postgresql://localhost:5432/mydb";
  private static final String DB_USER = "umang";
  private static final String DB_PASSWORD = "umang";

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
  }

  public static void getRandomProblems(String difficulty, int count) {
    String query = """
        SELECT id, link
        FROM problems
        WHERE LOWER(difficulty) = ?
        ORDER BY RANDOM()
        LIMIT ?
        """;

    try (Connection conn = connect();
        PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setString(1, difficulty.toLowerCase());
      stmt.setInt(2, count);
      ResultSet rs = stmt.executeQuery();

      boolean found = false;
      System.out.printf("--- %s PROBLEMS (random %d) ---%n", difficulty.toUpperCase(), count);
      while (rs.next()) {
        found = true;
        System.out.printf("%d | %s%n", rs.getInt("id"), rs.getString("link"));
      }

      if (!found) {
        System.out.printf("No %s problems found.%n", difficulty.toUpperCase());
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static void removeById(int id) {
    String query = "DELETE FROM problems WHERE id = ?";
    try (Connection conn = connect();
        PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setInt(1, id);
      int affected = stmt.executeUpdate();
      if (affected > 0) {
        System.out.printf("Deleted row with id = %d%n", id);
      } else {
        System.out.printf("No row found with id = %d%n", id);
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static void addProblem(int id, String url, String difficulty) {
    String query = "INSERT INTO problems (id, link, difficulty) VALUES (?, ?, ?)";

    try (Connection conn = connect();
        PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setInt(1, id);
      stmt.setString(2, url);
      stmt.setString(3, difficulty.toUpperCase());
      stmt.executeUpdate();
      System.out.printf("Added: %d | %s [%s]%n", id, url, difficulty.toUpperCase());

    } catch (SQLException e) {
      if (e.getSQLState().equals("23505")) { // unique_violation
        System.out.printf("Error: ID %d already exists.%n", id);
      } else {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("""
          Usage:
            java Problems get <difficulty> <count>
            java Problems remove <id>
            java Problems add <id> <url> <difficulty>
          """);
      return;
    }

    String command = args[0];

    try {
      switch (command) {
        case "get" -> {
          if (args.length != 3) {
            System.out.println("Usage: java Problems get <difficulty> <count>");
            return;
          }
          String difficulty = args[1].toLowerCase(Locale.ROOT);
          int count = Integer.parseInt(args[2]);
          if (!difficulty.matches("easy|medium|hard")) {
            System.out.println("Difficulty must be easy, medium, or hard.");
            return;
          }
          getRandomProblems(difficulty, count);
        }
        case "remove" -> {
          if (args.length != 2) {
            System.out.println("Usage: java Problems remove <id>");
            return;
          }
          int id = Integer.parseInt(args[1]);
          removeById(id);
        }
        case "add" -> {
          if (args.length != 4) {
            System.out.println("Usage: java Problems add <id> <url> <difficulty>");
            return;
          }
          int id = Integer.parseInt(args[1]);
          String url = args[2];
          String difficulty = args[3];
          addProblem(id, url, difficulty);
        }
        case "init" -> {
          try {
            MakeProblems.makeProblems();
          } catch (Exception e) {
            System.out.println("Exception occured while making problems");
          }
        }
        default -> System.out.println("Unknown command.");
      }
    } catch (NumberFormatException e) {
      System.out.println("Error: ID and count must be integers.");
    }
  }
}

class MakeProblems {

  public static void makeProblems() throws Exception {
    Map<String, String> linkToDifficultyMap = new HashMap<>();
    List<String> difficulty = new ArrayList<>();
    difficulty.add("EASY");
    difficulty.add("MEDIUM");
    difficulty.add("HARD");
    cloneDirectory();
    Stream<Path> path = Files.walk(Paths.get("./leetcode-company-wise-problems/"));
    path.filter((pathString) -> pathString.toString().contains("All.csv")).flatMap(
        pathString -> {
          try {
            return Files.lines(pathString);
          } catch (Exception e) {
            System.out.println("youre cooked");
            return Stream.empty();
          }
        }).forEach(
            (line) -> {
              String[] splitString = line.split("\\,");
              if (difficulty.contains(splitString[0]) && splitString[4].contains("leetcode"))
                linkToDifficultyMap.put(splitString[4], splitString[0]);
            });
    path.close();
    Connection connection = getConnection();
    dropTable(connection);
    addTable(connection);
    addValues(linkToDifficultyMap, connection);
    connection.close();
  }

  static Connection getConnection() {
    try {

      return DriverManager
          .getConnection("jdbc:postgresql://localhost:5432/mydb",
              "umang", "umang");
    } catch (Exception e) {
      return null;
    }
  }

  static void addValues(Map<String, String> linkToDifficultyMap, Connection connection) {
    String insertSql = "INSERT INTO PROBLEMS (ID, LINK, DIFFICULTY) VALUES (?, ?, ?)";
    try {

      PreparedStatement pstmt = connection.prepareStatement(insertSql);
      int count = 1;
      for (Map.Entry<String, String> entry : linkToDifficultyMap.entrySet()) {
        pstmt.setInt(1, count);
        pstmt.setString(2, entry.getKey()); // LINK
        pstmt.setString(3, entry.getValue()); // DIFFICULTY
        pstmt.addBatch();
        count++;
      }
      pstmt.executeBatch();
    } catch (SQLException e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());

    }
  }

  static void dropTable(Connection connection) {
    try {
      Statement stmt = connection.createStatement();
      String sql = "DROP TABLE IF EXISTS PROBLEMS";
      stmt.executeUpdate(sql);
      stmt.close();

    } catch (SQLException sqlException) {
      System.out.println("");
    }
  }

  static void addTable(Connection connection) {
    Statement stmt = null;
    try {
      Class.forName("org.postgresql.Driver");

      if (connection == null)
        return;
      stmt = connection.createStatement();
      String sql = "CREATE TABLE PROBLEMS " +
          "(ID INT NOT NULL," +
          "LINK           TEXT    NOT NULL, " +
          " DIFFICULTY         TEXT NOT NULL)";
      stmt.executeUpdate(sql);
      stmt.close();
    } catch (Exception e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
      System.exit(0);
    }
    System.out.println("Table created successfully");
  }

  static void cloneDirectory() {
    String repoUrl = "https://github.com/liquidslr/leetcode-company-wise-problems.git";
    String cloneDirectory = "./leetcode-company-wise-problems"; // destination folder

    try {
      ProcessBuilder builder = new ProcessBuilder("git", "clone", repoUrl, cloneDirectory);
      builder.redirectErrorStream(true); // combine stdout and stderr
      Process process = builder.start();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println(line);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode == 0) {
        System.out.println("Repository cloned successfully.");
      } else {
        System.out.println("Git clone failed with exit code " + exitCode);
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

}
