import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Main {

  public static final List<String> inputList = new ArrayList<>() {{
    add("-h");
    add("--help");
  }};
  public static String PROTOTYPES = "/prototypes/";
  public static String RAW_DATA = "/rawData/";
  public static String SQL_SCRIPTS = "/sqlScripts/";
  public static String SCRIPT_FULL_VERSION = "script_full_version.txt";
  private static List<String> prototypes = new ArrayList<>();
  private static List<String> rawData = new ArrayList<>();
  private static List<String> scripts = new ArrayList<>();

  //Separator
  private static String regex = "\t##\t";

  //Control String showing end of entry
  private static String regex2 = "$$";

  public static void main(String[] args) {

    commandLine(args);
    pathBuilder();

    try {
      getPrototypes();
      getRawDataFileNames();

      if (prototypes.size() != rawData.size()) {
        throw new IllegalArgumentException(
            "Number of prototype queries mismatches raw data input files");
      }

      //loop through all corresponding raw data and scripts
      //int level=0;
      for (int level = 0; level < rawData.size(); level++) {

        String rawFile = rawData.get(level);
        String scriptFile = scripts.get(level);

        //Read all input raw data one by one (prevents excessive memory usage) and generates a single query
        try (BufferedReader reader = new BufferedReader(
            new FileReader(RAW_DATA + rawFile))) {

          //Make sure there are no previous data in the output file
          clearFile(scriptFile);

          String line;
          while ((line = reader.readLine()) != null) {

            List<String> input = new ArrayList<>(Arrays.asList(line.split(regex)));

            if (!input.get(input.size() - 1).equals(regex2)) {
              System.out.println(
                  String.format("Parsing error. Raw data input misses end of line regex \"%s\"",
                      regex2));
              return;
            } else {
              input.remove(regex2);
            }

            //inserts input into the prototype and write down the query
            writeQuery(input, scriptFile, level);

          }

          // Merge all scripts into a single file
          mergeFiles();

        }
      }
    } catch (Exception e) {
      System.out.printf(e.getMessage());
    }
  }

  private static void commandLine(String[] args) {

    List consoleInput = Arrays.asList(args);

    if (consoleInput.isEmpty()) {
      return;
    }

    HashMap<String, String> options = new HashMap<>();
    options.put("-h", "help");
    options.put("-p", "set prototype query source folder, default - " + PROTOTYPES.replace("/",""));
    options.put("-r", "set raw data source folder, default - " + RAW_DATA.replace("/",""));
    options.put("-d", "set scripts destination folder, default - " + SQL_SCRIPTS.replace("/",""));
    options.put("-f", "set output file name, default - " + SCRIPT_FULL_VERSION);

    if (consoleInput.contains("-h")) {
      options.forEach((key, value) -> System.out.println(key + "      " + value));
    }

    if (consoleInput.contains("-p")) {
      PROTOTYPES = "/" + consoleInput.get(consoleInput.indexOf("-p")+1) + "/";
    }

    if (consoleInput.contains("-r")) {
      RAW_DATA = "/" + consoleInput.get(consoleInput.indexOf("-r")+1) + "/";
    }

    if (consoleInput.contains("-d")) {
      SQL_SCRIPTS = "/" + consoleInput.get(consoleInput.indexOf("-d")+1) + "/";
    }

    if (consoleInput.contains("-f")) {
      SCRIPT_FULL_VERSION = (String) consoleInput.get(consoleInput.indexOf("-f")+1);
    }

  }

  private static void pathBuilder() {
    Path currentRalativePath = Paths.get("");

    RAW_DATA = currentRalativePath.toAbsolutePath().toString() + RAW_DATA;
    SQL_SCRIPTS = currentRalativePath.toAbsolutePath().toString() + SQL_SCRIPTS;
    PROTOTYPES = currentRalativePath.toAbsolutePath().toString() + PROTOTYPES;
  }

  private static void getPrototypes() throws IOException {

    File directoryPath = new File(PROTOTYPES);

    File filesList[] = directoryPath.listFiles();

    //checks if folder and files exist
    checkPaths(filesList, PROTOTYPES);

    for (File file : filesList) {

      //readString() takes care of closing the file itself
      String content = Files.readString(Paths.get(file.getPath()));
      //write a single query prototype into the list
      prototypes.add(content);

      //Prepare names for output files
      scripts.add("script_" + file.getName());

    }
  }

  private static void getRawDataFileNames() {

    File directoryPath = new File(RAW_DATA);

    File filesList[] = directoryPath.listFiles();

    //checks if folder and files exist
    checkPaths(filesList, PROTOTYPES);

    for (File file : filesList) {

      //get file names from "../queryGenerator/rawData/"
      rawData.add(file.getName());
    }
  }

  private static void checkPaths(File fileList[], String path) {
    if (fileList == null) {
      throw new NullPointerException(String.format(
          "The following (default) folder is empty or missing \"%s\". It and its content is required ",
          path));
    }
  }

  private static void writeQuery(List<String> input, String scriptFile, int level)
      throws IOException {

    //Acquire number of placeholder in the prototype
    int placeHolders = countPlaceHolders(level);
    if (placeHolders > input.size()) {
      throw new IllegalArgumentException("Place holders are more than the input values");
    } else if (placeHolders < input.size()) {
      throw new IllegalArgumentException("Place holders are less than the input values");
    }

    String[] inputArray = input.toArray(new String[0]);
    String query = String.format(prototypes.get(level), inputArray) + "\n\n\n";

    try (BufferedWriter writer = new BufferedWriter(
        new FileWriter(SQL_SCRIPTS + scriptFile, true))) {
      writer.write(query);
    }
  }

  private static int countPlaceHolders(int level) {
    String query = prototypes.get(level);
    String placeHolder = "%s";

    int count = 0;
    int index = query.indexOf(placeHolder);

    while (index != -1) {
      count++;
      index = query.indexOf(placeHolder, index + 1);
    }

    return count;
  }

  private static void clearFile(String scriptFile) throws IOException {
    try (FileWriter writer = new FileWriter(SQL_SCRIPTS + scriptFile)) {
      writer.write("");
    } catch (FileNotFoundException e) {

    }
  }

  private static void mergeFiles() throws IOException {

    clearFile(SCRIPT_FULL_VERSION);

    File directoryPath = new File(SQL_SCRIPTS);

    File fileList[] = directoryPath.listFiles();

    for (File file : fileList) {
      try (BufferedWriter writer = new BufferedWriter(
          new FileWriter(SQL_SCRIPTS + SCRIPT_FULL_VERSION, true))) {

        String temp = Files.readString(Paths.get(file.getPath()));
        writer.write(temp);

      }
    }
  }

}
