import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class BlockModelParser {
    static final int MAX_PARENTS = 10;

    static final String modelsPath        = "src/main/java/blockmodels";
    static final String propertiesPath    = "src/main/java/blockstates";
    static final String modelsOutPath     = "src/main/java/output/models.txt";
    static final String propertiesOutPath = "src/main/java/output/block.properties";
    static final String indicesOutPath    = "src/main/java/output/indices.txt";

    static final String defaultBox = "    Box(vec3(0.0000000, 0.0000000, 0.0000000), vec3(0.0000000, 0.0000000, 0.0000000), vec2(0, 0)),";

    public static Double[] stringToDoubleArray(String string) {
        String[] items = string.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\\s", "").split(",");
        Double[] array = new Double[items.length];

        for(int i = 0; i < items.length; i++) array[i] = Double.valueOf(items[i]); return array;
    }

    public static String getUnformattedBlockName(String formattedBlockName) {
        return formattedBlockName.replaceAll("minecraft:", "").replaceAll("block/", "").replaceAll("\"", "");
    }

    public static JsonArray findParentsElements(String path) {
        JsonArray elements = null;

        try {
            Gson gson = new Gson();
            JsonReader parentReader = gson.newJsonReader(new FileReader(path));
            JsonObject parentTree = gson.fromJson(parentReader, JsonObject.class);

            for (int i = 0; i < MAX_PARENTS; i++) {
                elements = parentTree.getAsJsonArray("elements");
                if (elements != null || parentTree.get("parent") == null) break;

                path = Paths.get(path).getParent() + "\\" + getUnformattedBlockName(parentTree.get("parent").toString()) + ".json";
                parentReader = gson.newJsonReader(new FileReader(path));
                parentTree   = gson.fromJson(parentReader, JsonObject.class);

                parentReader.close();
            }
        } catch(IOException ioe) { ioe.printStackTrace(); }
        return elements;
    }

    public static StringBuilder getBlockDimensions(JsonArray elements, int xRot, int yRot) {
        StringBuilder blockModel = new StringBuilder();
        if(elements == null) return blockModel;

        for (int i = 0; i < elements.size(); i++) {
            JsonObject object = elements.get(i).getAsJsonObject();

            Double[] from = stringToDoubleArray(object.get("from").toString());
            Double[] to   = stringToDoubleArray(object.get("to").toString());

            Double[] boxSize = new Double[]{0.0, 0.0, 0.0};
            Double[] offSize = new Double[]{0.0, 0.0, 0.0};

            List<String> boxString = new ArrayList<>();
            List<String> offString = new ArrayList<>();

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
            symbols.setDecimalSeparator('.');
            DecimalFormat numFormat = new DecimalFormat("0.0000000", symbols);

            for (int j = 0; j < 3; j++) {
                boxSize[j] = (to[j] - from[j]) * 0.03125;
                offSize[j] = (from[j] + to[j]) * 0.03125;

                boxString.add(numFormat.format(boxSize[j]));
                offString.add(numFormat.format(offSize[j]));
            }
            String boxVec = "vec3(" + boxString.toString().replaceAll("\\[", "").replaceAll("]", "");
            String offVec = "vec3(" + offString.toString().replaceAll("\\[", "").replaceAll("]", "");
            String rotVec = "vec2(" + xRot + ", " + yRot + ")";

            blockModel.append("    Box(").append(boxVec).append("), ").append(offVec).append("), ").append(rotVec).append("),\n");
        }
        return blockModel;
    }

    public static void writeBlockModel() {
        try {
            long processStart = System.currentTimeMillis();

            File blockStatesFolder      = new File(propertiesPath);
            File[] blockStatesFiles     = blockStatesFolder.listFiles();
            ArrayList<File> missedFiles = new ArrayList<>();

            int blockId = 0;
            StringBuilder properties = new StringBuilder();

            StringBuilder models = new StringBuilder();
            models.append("const Box[] models = Box[](\n");

            StringBuilder indicesArray = new StringBuilder();
            indicesArray.append("const uint[] indices = uint[](\n");

            assert blockStatesFiles != null;
            for(File blockStatesFile : blockStatesFiles) {
                String path = blockStatesFile.getCanonicalPath();
                if (!path.endsWith(".json")) continue;

                Gson gson = new Gson();
                JsonReader reader = gson.newJsonReader(new FileReader(path));

                JsonObject tree = gson.fromJson(reader, JsonObject.class);
                JsonElement variants = tree.get("variants");

                if (variants == null) {
                    missedFiles.add(blockStatesFile);
                    continue;
                }

                JsonObject blockStates = variants.getAsJsonObject();
                StringBuilder blockModels = new StringBuilder();

                int endIndex = blockId;
                for (Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {
                    blockId++;

                    JsonObject element =
                            blockState.getValue().isJsonArray() ?
                                    blockState.getValue().getAsJsonArray().get(0).getAsJsonObject() :
                                    blockState.getValue().getAsJsonObject();

                    JsonElement model = element.get("model");

                    JsonElement xRotElement = element.get("x");
                    JsonElement yRotElement = element.get("y");
                    int xRot = xRotElement == null ? 0 : xRotElement.getAsInt();
                    int yRot = yRotElement == null ? 0 : yRotElement.getAsInt();

                    String blockProperties = blockState.getKey().equals("") ? "" : ":" + blockState.getKey().replaceAll(",", ":");
                    String blockName = getUnformattedBlockName(model.toString());

                    File modelFile = new File(modelsPath + "\\" + blockName + ".json");
                    String modelPath = modelFile.getCanonicalPath();

                    JsonArray elements = findParentsElements(modelPath);

                    int startIndex = endIndex;
                    endIndex      += elements == null ? 1 : elements.size();

                    StringBuilder blockDimensions = getBlockDimensions(elements, xRot, yRot);
                    if (blockDimensions.isEmpty()) blockDimensions = new StringBuilder(defaultBox + "\n");

                    properties.append("block.").append(blockId).append(" = ").append(blockName).append(blockProperties).append("\n");
                    blockModels.append("    // ").append(blockName).append(" (").append(blockId - 1).append(")\n").append(blockDimensions);
                    indicesArray.append("    (").append(elements == null ? 0 : elements.size()).append(" << 16) | (").append(startIndex).append(" << 16),\n");
                }
                reader.close();
                models.append(blockModels);
            }
            models.append(");");
            indicesArray.append(");");

            FileWriter propertiesWriter = new FileWriter(propertiesOutPath);
            propertiesWriter.write(properties.toString()); propertiesWriter.close();

            FileWriter modelsWriter = new FileWriter(modelsOutPath);
            modelsWriter.write(models.toString()); modelsWriter.close();

            FileWriter indicesWriter = new FileWriter(indicesOutPath);
            indicesWriter.write(indicesArray.toString()); indicesWriter.close();

            StringBuilder missedFilesList = new StringBuilder();
            for(File missedFile : missedFiles) {
                missedFilesList.append("\n-").append(" ").append(missedFile.getAbsolutePath());
            }

            long processEnd = System.currentTimeMillis();
            System.out.println("[SUCCESS] Wrote to files in " + (processEnd - processStart) + "ms.");
            System.out.println("[INFO] " + missedFiles.size() + " files were missed:" + missedFilesList);

        } catch(IOException ioe) { ioe.printStackTrace(); }
    }

    public static void main(String[] args) {
        writeBlockModel();
    }
}
