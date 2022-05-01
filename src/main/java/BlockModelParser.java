import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class BlockModelParser {

    static final String blockStatesPath     = "src/main/java/blockstates";
    static final String blockPropertiesPath = "src/main/java/output/block.properties";

    public static Integer[] stringToIntegerArray(String string) {
        String[] items = string.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s", "").split(",");

        int size = items.length;
        Integer[] array = new Integer[size];

        for(int i = 0; i < size; i++) {
            array[i] = Integer.parseInt(items[i]);
        }
        return array;
    }

    public static void main(String[] args) {

        try {
            long processStart = System.currentTimeMillis();

            File blockStatesFolder  = new File(blockStatesPath);
            File[] blockStatesFiles = blockStatesFolder.listFiles();

            int blockId = 0;
            StringBuilder properties = new StringBuilder();

            assert blockStatesFiles != null;
            for(File file : blockStatesFiles) {
                String path = file.getCanonicalPath();
                if(!path.endsWith(".json")) continue;

                Gson gson = new Gson();
                JsonReader reader = gson.newJsonReader(new FileReader(path));
                reader.setLenient(true);

                JsonObject tree      = gson.fromJson(reader, JsonObject.class);
                JsonElement variants = tree.get("variants");
                if(variants == null) continue;

                JsonObject blockStates = variants.getAsJsonObject();

                for(Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {

                    JsonElement model =
                            blockState.getValue().isJsonArray() ?
                            blockState.getValue().getAsJsonArray().get(0).getAsJsonObject().get("model") :
                            blockState.getValue().getAsJsonObject().get("model");

                    String blockProperties = blockState.getKey().equals("") ? "" : ":" + blockState.getKey().replaceAll(",", ":");
                    String blockName       = model.toString().replaceAll("minecraft:block/", "").replaceAll("\"", "");

                    blockId++;
                    properties.append("block.").append(blockId).append(" = ").append(blockName).append(blockProperties).append("\n\n");
                }
                reader.close();
            }

            long processEnd = System.currentTimeMillis();

            try {
                FileWriter writer = new FileWriter(blockPropertiesPath);
                writer.write(properties.toString()); writer.close();

                System.out.println("[SUCCESS] Wrote to the properties file in " + (processEnd - processStart) + "ms.");

            } catch (IOException ioe) { ioe.printStackTrace(); }

        } catch(IOException ioe) { ioe.printStackTrace(); }
    }
}
