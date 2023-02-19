import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
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

    public static Double[] stringToDoubleArray(String string) {
        String[] items = string.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\\s", "").split(",");
        Double[] array = new Double[items.length];

        for(int i = 0; i < items.length; i++) array[i] = Double.valueOf(items[i]); return array;
    }

    public static String getUnformattedBlockName(String formattedBlockName) {
        return formattedBlockName.replaceAll("minecraft:", "").replaceAll("block/", "").replaceAll("\"", "");
    }

    public static Double[] getRotation(JsonObject object) {
        JsonElement xRotElement = object.get("x");
        JsonElement yRotElement = object.get("y");
        double xRot = xRotElement == null ? 0 : xRotElement.getAsDouble();
        double yRot = yRotElement == null ? 0 : yRotElement.getAsDouble();

        return new Double[]{ xRot, yRot };
    }

    public static Box[] constructBoxArray(Double[] modelRotation, JsonArray elements) {
        if(elements == null) return null;
        List<Box> boxList = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            JsonObject object = elements.get(i).getAsJsonObject();

            Box box = new Box(new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0, 0.0});

            Double[] from = stringToDoubleArray(object.get("from").toString());
            Double[] to   = stringToDoubleArray(object.get("to").toString());

            for (int j = 0; j < 3; j++) {
                box.size[j]   = 1.0 / 16 * ((to[j] - from[j]) * 0.5);
                box.offset[j] = ((1.0 / 32 * ((from[j] + to[j]) * 0.5)) - 0.5) * 2.0 + 0.5;
            }

            box.modelRotation = modelRotation;

            JsonObject rotation = object.getAsJsonObject("rotation");

            if(rotation != null) {
                Double[] rotationOrigin = stringToDoubleArray(rotation.get("origin").toString());
                String axis             = rotation.get("axis").toString();
                Double angle            = rotation.get("angle").getAsDouble();

                box.boxRotation[0] = axis.contains("x") ? angle : 0;
                box.boxRotation[1] = axis.contains("y") ? angle : 0;

                for (int j = 0; j < 3; j++) {
                    box.rotationOrigin[j] = ((1.0 / 32 * rotationOrigin[j]) - 0.5) * 2.0 + 0.5;
                }
            }
            boxList.add(box);
        }
        return boxList.toArray(new Box[]{});
    }

    public static Parent findModelParent(File file, String stateName, JsonElement state) {
        List<Model> children = new ArrayList<>();

        JsonObject value = state.isJsonArray() ? state.getAsJsonArray().get(0).getAsJsonObject() : state.getAsJsonObject();

        String blockProperties = stateName.equals("") ? "" : ":" + stateName.replaceAll(",", ":");
        String blockName       = file.getName().replaceAll(".json", "");

        JsonElement model = value.get("model");
        File modelFile    = new File(modelsPath + "\\" + getUnformattedBlockName(model.toString()) + ".json");

        Double[] rotation = getRotation(value);

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
                    parent = new Model(name, constructBoxArray(rotation, elements)); break;
                }

                children.add(new Model(name, null));
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

        for(int i = 0; i < model.boxes.length; i++) {
            List<String> size           = new ArrayList<>();
            List<String> offset         = new ArrayList<>();
            List<String> rotationOrigin = new ArrayList<>();

            for(int j = 0; j < 3; j++) {
                size.add(numFormat.format(model.boxes[i].size[j]));
                offset.add(numFormat.format(model.boxes[i].offset[j]));
                rotationOrigin.add(numFormat.format(model.boxes[i].rotationOrigin[j]));
            }

            String boxVec  = "vec3(" + size.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";
            String offVec  = "vec3(" + offset.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";
            String rotVec0 = "vec2(" + model.boxes[i].modelRotation[0] + ", " + model.boxes[i].modelRotation[1] + ")";
            String rotVec1 = "vec2(" + model.boxes[i].boxRotation[0] + ", " + model.boxes[i].boxRotation[1] + ")";
            String oriVec  = "vec3(" + rotationOrigin.toString().replaceAll("\\[", "").replaceAll("]", "") + ")";

            boxDeclaration.append("    Box(").append(boxVec).append(", ").append(offVec).append(", ").append(rotVec0).append(", ").append(oriVec).append(", ").append(rotVec1).append("),\n");
        }
        return boxDeclaration;
    }
    static <T> T[] concatWithCollection(T[] array1, T[] array2) {
        List<T> resultList = new ArrayList<>(array1.length + array2.length);
        Collections.addAll(resultList, array1);
        Collections.addAll(resultList, array2);

        @SuppressWarnings("unchecked")
        //the type cast is safe as the array1 has the type T[]
        T[] resultArray = (T[]) Array.newInstance(array1.getClass().getComponentType(), 0);
        return resultList.toArray(resultArray);
    }

    public static Model combineModels(String name, Model model0, Model model1) {
        Model model = new Model(name, new Box[]{});

        model.name  = model0.name + model1.name.replace(name, "");
        model.boxes = concatWithCollection(model0.boxes, model1.boxes);

        return model;
    }

    public static Parent getParentFromName(Set<Parent> parents, String name) {
        for(Parent parent : parents) {
            if(parent.model.name.equals(name)) return parent;
        }
        return null;
    }

    public static void generateBlockFiles() {
        try {
            long processStart = System.currentTimeMillis();

            File blockStatesFolder  = new File(propertiesPath);
            File[] blockStatesFiles = blockStatesFolder.listFiles();
            List<Parent> parents    = new ArrayList<>();

            List<Set<Parent>> totalCases = new ArrayList<>();
            List<Set<String>> totalKeys  = new ArrayList<>();

            assert blockStatesFiles != null;
            for(File blockStatesFile : blockStatesFiles) {
                String path = blockStatesFile.getCanonicalPath();
                if (!path.endsWith(".json") || path.contains("inventory") || blockStatesFile.getName().equals("fire.json") || blockStatesFile.getName().equals("redstone_wire.json")) continue;

                Gson gson = new Gson();
                JsonReader reader = gson.newJsonReader(new FileReader(path));

                JsonObject tree     = gson.fromJson(reader, JsonObject.class);
                JsonElement objects = tree.get("variants");

                if(objects == null && tree.get("multipart") != null) {
                    objects = tree.get("multipart");

                    Set<Parent> cases = new HashSet<>();
                    Set<String> keys  = new HashSet<>();

                    for(int i = 0; i < objects.getAsJsonArray().size(); i++) {
                        JsonObject blockState = objects.getAsJsonArray().get(i).getAsJsonObject();
                        JsonElement apply = blockState.get("apply");

                        JsonObject when = new JsonObject();

                        if (blockState.get("when") != null) {
                            when = blockState.get("when").getAsJsonObject();
                        }

                        StringBuilder conditionBuilder = new StringBuilder();

                        for (Map.Entry<String, JsonElement> condition : when.entrySet()) {
                            if (when.entrySet().isEmpty()) break;

                            String value = condition.getValue().getAsString();
                            conditionBuilder.append(":").append(condition.getKey()).append("=").append(value);

                            keys.add(condition.getKey());
                        }

                        Parent parent = findModelParent(blockStatesFile, conditionBuilder.toString(), apply);
                        parent.model.name = blockStatesFile.getName().replace(".json", "") + conditionBuilder;
                        parent.model = new Multipart(parent.model.name, parent.model.boxes);

                        cases.add(parent);
                    }
                    totalCases.add(cases);
                    totalKeys.add(keys);

                } else {
                    JsonObject blockStates = objects.getAsJsonObject();

                    for (Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {

                        Parent parent = findModelParent(blockStatesFile, blockState.getKey(), blockState.getValue());
                        parents.add(parent);
                    }
                }
                reader.close();
            }

            List<List<List<Parent>>> totalCombinations = new ArrayList<>();

            for(Set<Parent> set : totalCases) {
                Map<String, Set<String>> models = new HashMap<>();
                Set<String> uniqueCases = new HashSet<>();

                Parent outsider = null;

                String blockName = "";

                for(Parent parent : set) {
                    blockName = parent.model.name;

                    if(!blockName.contains(":")) {
                        outsider = parent;
                        continue;
                    }

                    String[] subName = parent.model.name.split(":");
                    blockName = subName[0];

                    for(int i = 0; i < subName.length; i++) {
                        if(!subName[i].contains("=")) continue;
                        uniqueCases.add(subName[i].substring(subName[i].indexOf(":") + 1, subName[i].indexOf("=")));
                    }
                }

                for(String case0 : uniqueCases) {
                    Set<String> vals = new HashSet<>();

                    for(Parent parent : set) {
                        String[] subName = parent.model.name.split(":");

                        for (int i = 0; i < subName.length; i++) {
                            if(!subName[i].contains("=")) continue;

                            String case1 = subName[i].substring(subName[i].indexOf(":") + 1, subName[i].indexOf("="));
                            String val   = subName[i].substring(subName[i].indexOf("=") + 1);

                            if(case1.equals(case0)) {
                                vals.add(val);

                                if(blockName.contains("wall")) {
                                    vals.add("none"); vals.add("tall"); vals.add("low");
                                }

                                if (val.equals("false") || val.equals("true")) {
                                    vals.add("false"); vals.add("true");
                                }

                                if(case0.contains("leaves")) {
                                    vals.add("none");
                                }

                                if(case0.contains("level")) {
                                    vals.add("0");
                                }
                            }
                        }
                    }
                    models.put(case0, vals);
                }

                int totalPossibleCombinations = 1;

                for(Map.Entry<String, Set<String>> conditionCase : models.entrySet()) {
                    totalPossibleCombinations *= conditionCase.getValue().size();
                }

                int counter = 0;
                List<List<Parent>> allCombinations = new ArrayList<>();
                while(counter < totalPossibleCombinations) {
                    int copy = counter;
                    List<Parent> combination = new ArrayList<>();

                    for(Map.Entry<String, Set<String>> conditionCase : models.entrySet()) {
                        List<String> arr = new ArrayList<>(conditionCase.getValue());

                        String name   = blockName + ":" + conditionCase.getKey() + "=" + arr.get(copy % arr.size());
                        Parent parent = getParentFromName(set, name);

                        if(outsider != null) {
                            combination.add(outsider);
                        }

                        if(parent == null) {
                            parent = new Parent(new Model(name, new Box[]{}), new ArrayList<>());
                        }

                        combination.add(parent);

                        copy = (int) Math.floor(copy / arr.size());
                    }
                    allCombinations.add(combination);
                    counter++;
                }

                totalCombinations.add(allCombinations);
            }

            for(List<List<Parent>> modelCombinations : totalCombinations) {
                for(List<Parent> combination : modelCombinations) {
                    Model model = new Model("", new Box[]{});
                    String blockName = "";
                    for(Parent parent : combination) {
                        blockName = parent.model.name.contains(":") ? parent.model.name.substring(0, parent.model.name.indexOf(":")) : parent.model.name;
                        model = combineModels(blockName, model, parent.model);
                    }
                    model.name = blockName + model.name;
                    parents.add(new Parent(model, new ArrayList<>()));
                }
            }

            Box[] liquid = new Box[]{new Box(new Double[]{0.500000, 0.468750, 0.500000}, new Double[]{0.000000, -0.031250, 0.000000}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0}, new Double[]{0.000000, 0.000000, 0.000000})};
            parents.add(new Parent(new Model("water", liquid), new ArrayList<>()));
            parents.add(new Parent(new Model("lava", liquid), new ArrayList<>()));

            StringBuilder models = new StringBuilder();
            models.append("const Box[] models = Box[](\n");

            StringBuilder indices = new StringBuilder();
            indices.append("const uint[] indices = uint[](\n");

            StringBuilder properties = new StringBuilder();

            Set<Parent> parentsNoDuplicates = new HashSet<>();

            for(Parent parent : parents) {
                List<Model> children = new ArrayList<>();
                for(Parent duplicate : parents) {

                    if(parent.model.equals(duplicate.model)) {
                        if(duplicate.children.size() == 0) children.add(duplicate.model);
                        else                               children.add(duplicate.children.get(0));
                    }
                }
                parentsNoDuplicates.add(new Parent(parent.model, children));
            }

            int id = 0, endIndex = 0;
            int totalBoxes = 0;
            for(Parent parent : parentsNoDuplicates) {
                Model parentModel    = parent.model;
                List<Model> children = parent.children;

                if(parentModel.boxes == null) continue;
                Set<Box> boxNoDuplicates = new HashSet<>(Arrays.asList(parent.model.boxes));
                parent.model.boxes = boxNoDuplicates.toArray(new Box[0]);

                totalBoxes += parentModel.boxes.length;

                id++;
                int startIndex = endIndex;
                endIndex      += parentModel.boxes.length;

                StringBuilder boxDeclaration = constructBoxDeclaration(parentModel);
                StringBuilder childrenList   = new StringBuilder();

                for(Model child : children) {
                    childrenList.append(child.name).append(" ");
                }

                models.append("    /* [ID = ").append(id).append("] ").append(parentModel.name).append("\n(").append(children.size()).append(") ").append(childrenList).append("*/\n").append(boxDeclaration);
                indices.append("    (uint(").append(parentModel.boxes.length).append(") << 16) | uint(").append(startIndex).append("),\n");
                properties.append("block.").append(id).append(" = ").append(childrenList.toString().trim()).append("\n");
            }
            models.append(");").deleteCharAt(models.lastIndexOf(","));
            indices.append(");").deleteCharAt(indices.lastIndexOf(","));

            System.out.println("Total Boxes => " + totalBoxes);
            System.out.println("Total Floats => " + (totalBoxes * 13));

            String boxStruct = "struct Box {\n" +
                    "    vec3 size;\n" +
                    "    vec3 offset;\n" +
                    "    vec2 modelRotation;\n" +
                    "\n" +
                    "    vec3 pivot;\n" +
                    "    vec2 boxRotation;\n" +
                    "};";

            FileWriter propertiesWriter = new FileWriter(propertiesOutPath);
            FileWriter modelsWriter     = new FileWriter(modelsOutPath);

            propertiesWriter.write(properties.toString());       propertiesWriter.close();
            modelsWriter.write(boxStruct + "\n\n" + models + "\n\n" + indices + "\n"); modelsWriter.close();

            long processEnd = System.currentTimeMillis();
            System.out.println("[SUCCESS] Wrote to files in " + (processEnd - processStart) + "ms.");

        } catch(IOException ioe) { ioe.printStackTrace(); }
    }

    public static void main(String[] args) { generateBlockFiles(); }
}
