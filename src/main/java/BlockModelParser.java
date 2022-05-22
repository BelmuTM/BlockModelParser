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
    /*
        CREDITS:
        Bálint#1673 - Suggesting the idea of this project and helping to go through it
        fayer3#2332 - Help with compressing the models
     */

    static final int MAX_HIERARCHY_SIZE = 10;

    static final String modelsPath        = "src/main/java/blockmodels";
    static final String propertiesPath    = "src/main/java/blockstates";
    static final String modelsOutPath     = "src/main/java/output/models.txt";
    static final String propertiesOutPath = "src/main/java/output/block.properties";
    static final String indicesOutPath    = "src/main/java/output/indices.txt";

    public static Double[] stringToDoubleArray(String string) {
        String[] items = string.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\\s", "").split(",");
        Double[] array = new Double[items.length];

        for(int i = 0; i < items.length; i++) array[i] = Double.valueOf(items[i]); return array;
    }

    public static String getUnformattedBlockName(String formattedBlockName) {
        return formattedBlockName.replaceAll("minecraft:", "").replaceAll("block/", "").replaceAll("\"", "");
    }

    public static Integer[] getRotation(JsonObject object) {
        JsonElement xRotElement = object.get("x");
        JsonElement yRotElement = object.get("y");
        int xRot = xRotElement == null ? 0 : xRotElement.getAsInt();
        int yRot = yRotElement == null ? 0 : yRotElement.getAsInt();

        return new Integer[]{ xRot, yRot };
    }

    public static Box[] constructBoxArray(JsonArray elements) {
        if(elements == null) return null;
        List<Box> boxList = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            JsonObject object = elements.get(i).getAsJsonObject();

            Box box = new Box(new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0, 0.0});

            Double[] from = stringToDoubleArray(object.get("from").toString());
            Double[] to   = stringToDoubleArray(object.get("to").toString());

            for (int j = 0; j < 3; j++) {
                box.size[j]   = 1.0 / 32 * ((to[j] - from[j]) * 0.5);
                box.offset[j] = 1.0 / 32 * ((from[j] + to[j]) * 0.5);
            }

            JsonObject rotation = object.getAsJsonObject("rotation");

            if(rotation != null) {
                Double[] rotationOrigin = stringToDoubleArray(rotation.get("origin").toString());
                String axis             = rotation.get("axis").toString();
                Double angle            = rotation.get("angle").getAsDouble();

                box.rotation[0] = axis.contains("x") ? angle : 0;
                box.rotation[1] = axis.contains("y") ? angle : 0;

                for (int j = 0; j < 3; j++) {
                    box.rotationOrigin[j] = 1.0 / 32 * rotationOrigin[j];
                }
            }
            boxList.add(box);
        }
        return boxList.toArray(new Box[]{});
    }

    public static Parent computeModelParent(File file, Map.Entry<String, JsonElement> blockState) {
        List<Model> children = new ArrayList<>();

        JsonObject value = blockState.getValue().isJsonArray() ?
                           blockState.getValue().getAsJsonArray().get(0).getAsJsonObject() :
                           blockState.getValue().getAsJsonObject();

        String blockProperties = blockState.getKey().equals("") ? "" : ":" + blockState.getKey().replaceAll(",", ":");
        String blockName       = file.getName().replaceAll(".json", "");

        JsonElement model = value.get("model");
        File modelFile    = new File(modelsPath + "\\" + getUnformattedBlockName(model.toString()) + ".json");

        Integer[] rotation = getRotation(value);
        Model parent = null;

        try {
            String path = modelFile.getCanonicalPath();

            Gson gson = new Gson();
            JsonReader parentReader = gson.newJsonReader(new FileReader(path));
            JsonObject parentTree = gson.fromJson(parentReader, JsonObject.class);

            String name = blockName + blockProperties;

            for (int i = 0; i < MAX_HIERARCHY_SIZE; i++) {
                JsonArray elements      = parentTree.getAsJsonArray("elements");
                JsonElement parentField = parentTree.get("parent");

                if (elements != null || parentField == null) {
                    parent = new Model(name, constructBoxArray(elements), rotation); break;
                }

                children.add(new Model(name, null, null));
                name = getUnformattedBlockName(parentField.toString());

                path = Paths.get(path).getParent() + "\\" + name + ".json";
                parentReader = gson.newJsonReader(new FileReader(path));
                parentTree   = gson.fromJson(parentReader, JsonObject.class);

                parentReader.close();
            }

        } catch(IOException ioe) { ioe.printStackTrace(); }
        return new Parent(parent, children);
    }

    public static StringBuilder constructBoxDeclaration(Model model) {
        StringBuilder boxDeclaration = new StringBuilder();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        DecimalFormat numFormat = new DecimalFormat("0.000000", symbols);

        String modelRotVec = "vec2(" + model.rotation[0] + ", " + model.rotation[1] + ")";

        for(int i = 0; i < model.boxes.length; i++) {
            List<String> size           = new ArrayList<>();
            List<String> offset         = new ArrayList<>();
            List<String> rotationOrigin = new ArrayList<>();

            for(int j = 0; j < 3; j++) {
                size.add(numFormat.format(model.boxes[i].size[j]));
                offset.add(numFormat.format(model.boxes[i].offset[j]));
                rotationOrigin.add(numFormat.format(model.boxes[i].rotationOrigin[j]));
            }

            String boxVec = "vec3(" + size.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";
            String offVec = "vec3(" + offset.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";
            String oriVec = "vec3(" + rotationOrigin.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";
            String rotVec = "vec2(" + model.boxes[i].rotation[0] + ", " + model.boxes[i].rotation[1] + ")";

            boxDeclaration.append("    Box(").append(boxVec).append(", ").append(offVec).append(", ").append(modelRotVec).append(", ").append(oriVec).append(", ").append(rotVec).append("),\n");
        }
        return boxDeclaration;
    }

    public static void generateBlockFiles() {
        try {
            long processStart = System.currentTimeMillis();

            File blockStatesFolder  = new File(propertiesPath);
            File[] blockStatesFiles = blockStatesFolder.listFiles();

            List<File> missedFiles = new ArrayList<>();
            List<Parent> parents   = new ArrayList<>();

            assert blockStatesFiles != null;
            for(File blockStatesFile : blockStatesFiles) {
                String path = blockStatesFile.getCanonicalPath();
                if (!path.endsWith(".json")) continue;

                Gson gson = new Gson();
                JsonReader reader = gson.newJsonReader(new FileReader(path));

                JsonObject tree     = gson.fromJson(reader, JsonObject.class);
                JsonElement objects = tree.get("variants");

                if (objects == null) {
                    missedFiles.add(blockStatesFile);
                    continue;
                }

                JsonObject blockStates = objects.getAsJsonObject();

                for (Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {

                    Parent parent = computeModelParent(blockStatesFile, blockState);
                    parents.add(parent);
                }
                reader.close();
            }

            StringBuilder models = new StringBuilder();
            models.append("const Box[] models = Box[](\n");

            StringBuilder indices = new StringBuilder();
            indices.append("const uint[] indices = uint[](\n");

            StringBuilder properties = new StringBuilder();

            List<Parent> hierarchyNoDuplicates = new ArrayList<>();

            for(Parent parent : parents) {
                List<Model> children = new ArrayList<>();
                for(Parent duplicate : parents) {

                    if(parent.model.equals(duplicate.model)) {
                        if(duplicate.children.size() == 0) children.add(duplicate.model);
                        else                               children.add(duplicate.children.get(0));
                    }
                }

                Parent newParent = new Parent(parent.model, children);

                if(!hierarchyNoDuplicates.contains(newParent))
                    hierarchyNoDuplicates.add(newParent);
                else
                    System.out.println(parent.model.name);
            }

            int id = 0, endIndex = 0;
            for(Parent parent : hierarchyNoDuplicates) {
                Model parentModel    = parent.model;
                List<Model> children = parent.children;

                if(parentModel.boxes == null) continue;

                id++;
                int startIndex = endIndex;
                endIndex      += parentModel.boxes.length;

                StringBuilder boxDeclaration = constructBoxDeclaration(parentModel);
                StringBuilder childrenList   = new StringBuilder();

                for(Model child : children) {
                    childrenList.append(child.name).append(" ");
                }

                models.append("    // ").append(parentModel.name).append(" (").append(id).append(")\n    // (").append(children.size()).append(") ").append(childrenList).append("\n").append(boxDeclaration);
                indices.append("    (uint(").append(parentModel.boxes.length).append(") << 16) | uint(").append(startIndex).append("),\n");
                properties.append("block.").append(id).append(" = ").append(childrenList.toString().trim()).append("\n");
            }
            models.append(");"); indices.append(");");

            FileWriter propertiesWriter = new FileWriter(propertiesOutPath);
            FileWriter modelsWriter     = new FileWriter(modelsOutPath);
            FileWriter indicesWriter    = new FileWriter(indicesOutPath);

            propertiesWriter.write(properties.toString()); propertiesWriter.close();
            modelsWriter.write(models.toString());         modelsWriter.close();
            indicesWriter.write(indices.toString());       indicesWriter.close();

            StringBuilder missedFilesList = new StringBuilder();
            for(File missedFile : missedFiles)
                missedFilesList.append("\n-").append(" ").append(missedFile.getAbsolutePath());

            long processEnd = System.currentTimeMillis();
            System.out.println("[SUCCESS] Wrote to files in " + (processEnd - processStart) + "ms.");
            System.out.println("[INFO] " + missedFiles.size() + " files were missed:" + missedFilesList);

        } catch(IOException ioe) { ioe.printStackTrace(); }
    }

    public static void main(String[] args) { generateBlockFiles(); }
}
