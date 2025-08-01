import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class BlockModelParser {
    /*
        [Credits]:
            Bálint - Suggesting the idea of this project and helping to go through it
            fayer3 - Help with compressing the models
     */

    static final int MAX_HIERARCHY_SIZE = 20;

    static final String modelsPath        = "src/main/java/blockmodels";
    static final String propertiesPath    = "src/main/java/blockstates";
    static final String propertiesOutPath = "src/main/java/output/block.properties";
    static final String modelDataPath     = "src/main/java/output/model_data.dat";

    static final String individualBlocksList = "jack_o_lantern pearlescent_froglight verdant_froglight ochre_froglight shroomlight glass magma_block soul_torch torch soul_wall_torch wall_torch soul_lantern lantern soul_campfire:lit=true campfire:lit=true sea_lantern glowstone black_stained_glass blue_stained_glass brown_stained_glass cyan_stained_glass gray_stained_glass green_stained_glass light_blue_stained_glass light_gray_stained_glass lime_stained_glass magenta_stained_glass orange_stained_glass pink_stained_glass purple_stained_glass red_stained_glass white_stained_glass yellow_stained_glass tinted_glass ice";

    public static Double[] stringToDoubleArray(String string) {
        String[] items = string.replaceAll("\\[", "").replaceAll("]", "").replaceAll("\\s", "").split(",");
        Double[] array = new Double[items.length];

        for (int i = 0; i < items.length; i++) array[i] = Double.valueOf(items[i]); return array;
    }

    public static String getUnformattedBlockName(String formattedBlockName) {
        return formattedBlockName.replaceAll("minecraft:", "").replaceAll("block/", "").replaceAll("\"", "").replaceAll("/", "");
    }

    public static Double[] getRotation(JsonObject object) {
        JsonElement xRotElement = object.get("x");
        JsonElement yRotElement = object.get("y");
        double xRot = xRotElement == null ? 0 : xRotElement.getAsDouble();
        double yRot = yRotElement == null ? 0 : yRotElement.getAsDouble();

        return new Double[]{xRot, yRot};
    }

    public static Box[] constructBoxArray(Double[] modelRotation, JsonArray elements, int uvLock) {
        if (elements == null) return null;
        List<Box> boxList = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            JsonObject object = elements.get(i).getAsJsonObject();

            Box box = new Box(new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, 0);

            Double[] from = stringToDoubleArray(object.get("from").toString());
            Double[] to   = stringToDoubleArray(object.get("to").toString());

            for (int j = 0; j < 3; j++) {
                box.size[j]   = 1.0 / 16 * ((to[j] - from[j]) * 0.5);
                box.offset[j] = ((1.0 / 32 * ((from[j] + to[j]) * 0.5)) - 0.5) * 2.0 + 0.5;
            }

            box.modelRotation = modelRotation;

            JsonObject rotation = object.getAsJsonObject("rotation");

            if (rotation != null) {
                Double[] pivot = stringToDoubleArray(rotation.get("origin").toString());
                String axis    = rotation.get("axis").toString();
                double angle   = rotation.get("angle").getAsDouble();

                box.boxRotation[0] = axis.contains("x") ? angle : 0;
                box.boxRotation[1] = axis.contains("y") ? angle : 0;
                box.boxRotation[2] = axis.contains("z") ? angle : 0;

                for (int j = 0; j < 3; j++) {
                    box.pivot[j] = ((1.0 / 32 * pivot[j]) - 0.5) * 2.0 + 0.5;
                }
            }

            box.uvLock = uvLock;

            boxList.add(box);
        }
        return boxList.toArray(new Box[]{});
    }

    public static Parent findModelParent(File file, String stateName, JsonElement state) {
        List<Model> children = new ArrayList<>();

        JsonObject value = state.isJsonArray() ? state.getAsJsonArray().get(0).getAsJsonObject() : state.getAsJsonObject();

        String blockProperties = stateName.isEmpty() ? "" : ":" + stateName.replaceAll(",", ":");
        String blockName       = file.getName().replaceAll(".json", "");

        JsonElement model = value.get("model");
        File modelFile    = new File(modelsPath + "\\" + getUnformattedBlockName(model.toString()) + ".json");

        Double[] rotation = getRotation(value);

        int uvLock = value.get("uvlock") == null ? 0 : (value.get("uvlock").getAsString().equals("true") ? 1 : 0);

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
                    parent = new Model(name, constructBoxArray(rotation, elements, uvLock)); break;
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

        if (model0.boxes == null) model0.boxes = new Box[]{};
        if (model1.boxes == null) model1.boxes = new Box[]{};

        model.boxes = concatWithCollection(model0.boxes, model1.boxes);

        return model;
    }

    public static Parent getParentFromName(Set<Parent> parents, String name) {
        for (Parent parent : parents) {
            if (parent.model.name.equals(name)) return parent;
        }
        return null;
    }

    public static void generateBlockFiles() {
        try {
            long processStart = System.currentTimeMillis();

            Set<Parent> individualBlocks = new HashSet<>();

            Box[] water = new Box[]{new Box(new Double[]{0.500000, 0.468750, 0.500000}, new Double[]{0.000000, -0.031250, 0.000000}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.000000, 0.000000, 0.000000}, 0)};
            Box[] lava  = new Box[]{new Box(new Double[]{0.500000, 0.468750, 0.500000}, new Double[]{0.000000, -0.031250, 0.000000}, new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0, 0.0}, new Double[]{0.000000, 0.000000, 0.000000}, 1)};

            Model waterModel = new Model("water", water);
            Model lavaModel  = new Model("lava", lava);

            List<Model> waterChildren = new ArrayList<>();
            List<Model> lavaChildren  = new ArrayList<>();

            waterChildren.add(waterModel);
            lavaChildren.add(lavaModel);

            individualBlocks.add(new Parent(waterModel, waterChildren));
            individualBlocks.add(new Parent(lavaModel, lavaChildren));

            File blockStatesFolder  = new File(propertiesPath);
            File[] blockStatesFiles = blockStatesFolder.listFiles();
            List<Parent> parents    = new ArrayList<>();

            List<Set<Parent>> totalCases = new ArrayList<>();

            assert blockStatesFiles != null;
            for (File blockStatesFile : blockStatesFiles) {
                String path = blockStatesFile.getCanonicalPath();

                if (!path.endsWith(".json") || path.contains("inventory"))
                    continue;

                Gson gson = new Gson();
                JsonReader reader = gson.newJsonReader(new FileReader(path));

                JsonObject tree = gson.fromJson(reader, JsonObject.class);
                JsonElement objects = tree.get("variants");

                if (objects == null && tree.get("multipart") != null) {
                    objects = tree.get("multipart");

                    //System.out.println(blockStatesFile.getName());

                    Set<Parent> cases = new HashSet<>();

                    for (int i = 0; i < objects.getAsJsonArray().size(); i++) {
                        JsonObject blockState = objects.getAsJsonArray().get(i).getAsJsonObject();
                        JsonElement apply = blockState.get("apply");

                        JsonObject when;

                        List<Set<Map.Entry<String, JsonElement>>> conditionSets = new ArrayList<>();

                        if (blockState.get("when") != null) {
                            when = blockState.get("when").getAsJsonObject();

                            if (when.get("AND") != null) {
                                Map<String, JsonElement> merged = new LinkedHashMap<>();
                                JsonArray and = when.get("AND").getAsJsonArray();

                                for (int j = 0; j < and.size(); j++) {
                                    JsonObject clause = and.get(j).getAsJsonObject();
                                    for (Map.Entry<String, JsonElement> entry : clause.entrySet()) {
                                        if (merged.containsKey(entry.getKey())) {
                                            System.err.println("Duplicate key in AND: " + entry.getKey());
                                        }
                                        merged.put(entry.getKey(), entry.getValue().deepCopy());
                                    }
                                }

                                conditionSets.add(new HashSet<>(merged.entrySet()));

                            } else if (when.get("OR") != null) {
                                JsonArray or = when.get("OR").getAsJsonArray();

                                for (int j = 0; j < or.size(); j++) {
                                    JsonObject clause = or.get(j).getAsJsonObject();
                                    Map<String, JsonElement> singleClauseCopy = new LinkedHashMap<>();

                                    for (Map.Entry<String, JsonElement> entry : clause.entrySet()) {
                                        singleClauseCopy.put(entry.getKey(), entry.getValue().deepCopy());
                                    }

                                    conditionSets.add(new HashSet<>(singleClauseCopy.entrySet()));
                                }

                            } else {
                                Map<String, JsonElement> fallback = new LinkedHashMap<>();
                                for (Map.Entry<String, JsonElement> entry : when.entrySet()) {
                                    fallback.put(entry.getKey(), entry.getValue().deepCopy());
                                }

                                conditionSets.add(new HashSet<>(fallback.entrySet()));
                            }
                        } else {
                            conditionSets.add(Collections.emptySet());
                        }

                        for (Set<Map.Entry<String, JsonElement>> conditionSet : conditionSets) {
                            StringBuilder conditionBuilder = new StringBuilder();

                            for (Map.Entry<String, JsonElement> condition : conditionSet) {
                                if (conditionSet.isEmpty()) break;

                                String value = condition.getValue().getAsString();
                                conditionBuilder.append(":").append(condition.getKey()).append("=").append(value);
                            }

                            Parent parent     = findModelParent(blockStatesFile, conditionBuilder.toString(), apply);
                            parent.model.name = blockStatesFile.getName().replace(".json", "") + conditionBuilder;
                            parent.model      = new Multipart(parent.model.name, parent.model.boxes);

                            //System.out.println(parent.model.name);

                            cases.add(parent);
                        }
                    }
                    totalCases.add(cases);

                } else {
                    JsonObject blockStates = objects.getAsJsonObject();

                    for (Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {
                        Parent parent = findModelParent(blockStatesFile, blockState.getKey(), blockState.getValue());
                        parents.add(parent);
                    }
                }
                reader.close();
            }

            for (Parent parent : parents) {
                for (Model model : parent.children) {
                    if (Arrays.asList(individualBlocksList.split(" ")).contains(model.name.contains(":") ? model.name.substring(0, model.name.indexOf(":")) : model.name)) {
                        List<Model> children = new ArrayList<>();
                        children.add(model);
                        model.boxes = parent.model.boxes;

                        individualBlocks.add(new Parent(new Multipart(model.name, model.boxes), children));
                    }
                }
            }

            List<List<List<Parent>>> totalCombinations = new ArrayList<>();

            for (Set<Parent> set : totalCases) {
                Map<String, Set<String>> models = new HashMap<>();
                Set<String> uniqueCases = new HashSet<>();

                Parent outsider = null;

                String blockName = "";

                for (Parent parent : set) {
                    blockName = parent.model.name;

                    if (!blockName.contains(":")) {
                        outsider = parent;
                        continue;
                    }

                    String[] subName = parent.model.name.split(":");
                    blockName = subName[0];

                    for (String s : subName) {
                        if (!s.contains("=")) continue;
                        uniqueCases.add(s.substring(s.indexOf(":") + 1, s.indexOf("=")));
                    }
                }

                for (String case0 : uniqueCases) {
                    Set<String> vals = new HashSet<>();

                    for (Parent parent : set) {
                        String[] subName = parent.model.name.split(":");

                        for (String s : subName) {
                            if (!s.contains("=")) continue;

                            String case1 = s.substring(s.indexOf(":") + 1, s.indexOf("="));
                            String val   = s.substring(s.indexOf("=") + 1);

                            if (case1.equals(case0)) {

                                if (val.contains("|")) {
                                    String[] subVal = val.split("\\|");
                                    vals.addAll(Arrays.asList(subVal));
                                    continue;
                                }

                                vals.add(val);

                                if (blockName.contains("wall")) {
                                    vals.add("none");
                                    vals.add("tall");
                                    vals.add("low");
                                }

                                if (val.equals("false") || val.equals("true")) {
                                    vals.add("false");
                                    vals.add("true");
                                }

                                if (case0.contains("leaves")) {
                                    vals.add("none");
                                }

                                if (case0.contains("level")) {
                                    vals.add("0");
                                }
                            }
                        }
                    }
                    models.put(case0, vals);
                }

                int totalPossibleCombinations = 1;

                for (Map.Entry<String, Set<String>> conditionCase : models.entrySet()) {
                    totalPossibleCombinations *= conditionCase.getValue().size();
                }

                int counter = 0;
                List<List<Parent>> allCombinations = new ArrayList<>();
                while (counter < totalPossibleCombinations) {
                    int copy = counter;
                    List<Parent> combination = new ArrayList<>();

                    for (Map.Entry<String, Set<String>> conditionCase : models.entrySet()) {
                        List<String> arr = new ArrayList<>(conditionCase.getValue());

                        String name = blockName + ":" + conditionCase.getKey() + "=" + arr.get(copy % arr.size());
                        Parent parent = getParentFromName(set, name);

                        if (outsider != null) {
                            combination.add(outsider);
                        }

                        if (parent == null) {
                            parent = new Parent(new Model(name, new Box[]{}), new ArrayList<>());
                        }

                        combination.add(parent);

                        copy = (int) Math.floor((double) copy / arr.size());
                    }
                    allCombinations.add(combination);
                    counter++;
                }

                totalCombinations.add(allCombinations);
            }

            for (List<List<Parent>> modelCombinations : totalCombinations) {
                for (List<Parent> combination : modelCombinations) {
                    Model model = new Model("", new Box[]{});
                    String blockName = "";
                    for (Parent parent : combination) {
                        blockName = parent.model.name.contains(":") ? parent.model.name.substring(0, parent.model.name.indexOf(":")) : parent.model.name;
                        model     = combineModels(blockName, model, parent.model);
                    }
                    model.name = blockName + model.name;
                    parents.add(new Parent(model, new ArrayList<>()));
                }
            }

            Set<Parent> parentsNoDuplicates = new HashSet<>();

            for (Parent parent : parents) {
                List<Model> children = new ArrayList<>();
                for (Parent duplicate : parents) {

                    if (parent.model.equals(duplicate.model)) {
                        if (duplicate.children.isEmpty()) children.add(duplicate.model);
                        else                              children.add(duplicate.children.get(0));
                    }
                }
                if (parent.model.boxes != null) parentsNoDuplicates.add(new Parent(parent.model, children));
            }

            List<Parent> sorted        = parentsNoDuplicates.stream().sorted().toList();
            List<Parent> parentsSorted = new ArrayList<>(sorted);

            parentsSorted.addAll(individualBlocks.stream().sorted().toList());

            StringBuilder properties    = new StringBuilder();
            FileWriter propertiesWriter = new FileWriter(propertiesOutPath);

            List<List<Integer>> totalData = new ArrayList<>();

            int id = 0;
            int maxBoxes = -1;
            for (Parent parent : parentsSorted) {

                if (parent.model.name.contains("torch")) {
                    parent.model.boxes = new Box[]{ parent.model.boxes[0] };

                    parent.model.boxes[0].size[0] = 0.0625;
                    parent.model.boxes[0].size[1] = 0.3125;
                    parent.model.boxes[0].size[2] = 0.0625;
                }

                Set<Box> boxNoDuplicates = new HashSet<>(Arrays.asList(parent.model.boxes));
                parent.model.boxes = boxNoDuplicates.toArray(new Box[0]);

                maxBoxes = Math.max(parent.model.boxes.length, maxBoxes);
                id++;

                StringBuilder childrenList = new StringBuilder();
                for (Model child : parent.children) {
                    if (Arrays.asList(individualBlocksList.split(" ")).contains(child.name.contains(":") ? child.name.substring(0, child.name.indexOf(":")) : child.name) && !parent.model.name.equals(child.name)) continue;
                    childrenList.append(child.name).append(" ");
                }

                properties.append("block.").append(id).append(" = ").append(childrenList.toString().trim()).append("\n");

                List<Integer> data = new ArrayList<>();

                int boxCountX = parent.model.boxes.length;
                int boxCountY = 0;
                int boxCountZ = 0;

                data.add(boxCountX); data.add(boxCountY); data.add(boxCountZ);

                for (Box box : parent.model.boxes) {
                    int sizeX = (int) (box.size[0] * 255.0);
                    int sizeY = (int) (box.size[1] * 255.0);
                    int sizeZ = (int) (box.size[2] * 255.0);

                    data.add(sizeX); data.add(sizeY); data.add(sizeZ);

                    int offsetX = (int) ((box.offset[0] * 0.5 + 0.5) * 255);
                    int offsetY = (int) ((box.offset[1] * 0.5 + 0.5) * 255);
                    int offsetZ = (int) ((box.offset[2] * 0.5 + 0.5) * 255);

                    data.add(offsetX); data.add(offsetY); data.add(offsetZ);

                    int modelRotationX = box.modelRotation[0].intValue() * 255 / 270;
                    int modelRotationY = box.modelRotation[1].intValue() * 255 / 270;
                    int modelRotationZ = box.uvLock;

                    data.add((modelRotationX)); data.add(modelRotationY); data.add(modelRotationZ);

                    int pivotX = (int) ((box.pivot[0] * 0.5 + 0.5) * 255);
                    int pivotY = (int) ((box.pivot[1] * 0.5 + 0.5) * 255);
                    int pivotZ = (int) ((box.pivot[2] * 0.5 + 0.5) * 255);

                    data.add(pivotX); data.add(pivotY); data.add(pivotZ);

                    int boxRotationX = (box.boxRotation[0].intValue() + 90) * 255 / 180;
                    int boxRotationY = (box.boxRotation[1].intValue() + 90) * 255 / 180;
                    int boxRotationZ = (box.boxRotation[2].intValue() + 90) * 255 / 180;

                    data.add(boxRotationX); data.add(boxRotationY); data.add(boxRotationZ);
                }

                totalData.add(data);
            }

            FileOutputStream outputStream     = new FileOutputStream(modelDataPath);
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            final int pixelsPerBox = 5;
            int maxPixels = (maxBoxes * pixelsPerBox) + 1;

            final int bytesPerPixel = 3;

            int totalByteCount = 0;

            for (List<Integer> data : totalData) {
                for (Integer x : data) {
                    dataOutputStream.writeByte(x);
                    totalByteCount++;
                }

                int padding = ((maxPixels * bytesPerPixel) - data.size()) + bytesPerPixel;

                for (int i = 0; i < padding; i++) {
                    dataOutputStream.writeByte(0);
                    totalByteCount++;
                }
            }
            dataOutputStream.close();

            propertiesWriter.write(properties.toString());
            propertiesWriter.close();

            System.out.println("[INFO] Image resolution: " + (maxPixels + 1) + " x " + parentsSorted.size());
            System.out.println("[INFO] Image size: " + totalByteCount + " bytes");

            long processEnd = System.currentTimeMillis();
            System.out.println("[SUCCESS] Wrote to files in " + (processEnd - processStart) + "ms.");

        } catch(IOException ioe) { ioe.printStackTrace(); }
    }

    public static void main(String[] args) { generateBlockFiles(); }
}
