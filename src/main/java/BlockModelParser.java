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

    static final String modelsPath        = "src/main/java/blockmodels";
    static final String propertiesPath    = "src/main/java/blockstates";
    static final String propertiesOutPath = "src/main/java/output/block.properties";
    static final String modelDataPath     = "src/main/java/output/model_data.dat";
    static final String idMappingsOutPath = "src/main/java/output/block_id_mappings.glsl";

    static final Box[] fluidModel = new Box[]{
        new Box(
            new Double[]{0.500000, 0.468750, 0.500000},
            new Double[]{0.000000, -0.031250, 0.000000},
            new Double[]{0.0, 0.0},
            new Double[]{0.0, 0.0, 0.0},
            new Double[]{0.000000, 0.000000, 0.000000},
            0
        )
    };

    static final Model waterModel = new Model("water", fluidModel);
    static final Model lavaModel  = new Model("lava", fluidModel);

    static final int MAX_MODEL_HIERARCHY_SIZE = 20;

    static final int MAX_ID_MAPPING_LINE_WIDTH = 60;

    public static Double[] stringToDoubleArray(String string) {
        String[] items = string
            .replaceAll("\\[", "")
            .replace("]", "")
            .replaceAll("\\s", "")
            .split(",");

        Double[] array = new Double[items.length];

        for (int i = 0; i < items.length; i++) array[i] = Double.valueOf(items[i]);
        return array;
    }

    public static String getUnformattedBlockName(String formattedBlockName) {
        return formattedBlockName
            .replace("minecraft:", "")
            .replace("block/", "")
            .replace("\"", "")
            .replace("/", "");
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

            Box box = new Box(
                new Double[]{0.0, 0.0, 0.0},    // size
                new Double[]{0.0, 0.0, 0.0},    // offset
                new Double[]{0.0, 0.0},         // model rotation
                new Double[]{0.0, 0.0, 0.0},    // box rotation
                new Double[]{0.0, 0.0, 0.0},    // pivot
                0                               // uv lock
            );

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

                if (rotation.get("angle") != null) {
                    String axis  = rotation.get("axis").toString();
                    double angle = rotation.get("angle").getAsDouble();

                    box.boxRotation[0] = axis.contains("x") ? angle : 0;
                    box.boxRotation[1] = axis.contains("y") ? angle : 0;
                    box.boxRotation[2] = axis.contains("z") ? angle : 0;
                }

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

        JsonObject value = state.isJsonArray()
            ? state.getAsJsonArray().get(0).getAsJsonObject()
            : state.getAsJsonObject();

        String blockProperties = stateName.isEmpty() ? "" : ":" + stateName.replace(",", ":");
        String blockName       = file.getName().replaceAll(".json", "");

        JsonElement model = value.get("model");
        File modelFile    = new File(modelsPath + File.separator + getUnformattedBlockName(model.toString()) + ".json");

        Double[] rotation = getRotation(value);

        int uvLock = value.get("uvlock") != null && value.get("uvlock").getAsString().equals("true") ? 0 : 1;

        Model parent = null;

        try {
            String path = modelFile.getCanonicalPath();

            Gson gson = new Gson();

            JsonReader parentReader = gson.newJsonReader(new FileReader(path));
            JsonObject parentTree   = gson.fromJson(parentReader, JsonObject.class);

            String name = blockName + blockProperties;

            for (int i = 0; i < MAX_MODEL_HIERARCHY_SIZE; i++) {
                JsonArray elements      = parentTree.getAsJsonArray("elements");
                JsonElement parentField = parentTree.get("parent");

                if (elements != null || parentField == null) {
                    parent = new Model(name, constructBoxArray(rotation, elements, uvLock));
                    break;
                }

                children.add(new Model(name, null));

                name = getUnformattedBlockName(parentField.toString());

                path = Paths.get(path).getParent() + File.separator + name + ".json";

                parentReader = gson.newJsonReader(new FileReader(path));
                parentTree   = gson.fromJson(parentReader, JsonObject.class);

                parentReader.close();
            }

        } catch(IOException ioe) {
            System.out.println("[ERROR]: Failed to parse file \"" + modelFile.getParent() + "\".");
        }

        return new Parent(parent, children);
    }

    static <T> T[] concatenateWithCollection(T[] array1, T[] array2) {
        List<T> resultList = new ArrayList<>(array1.length + array2.length);

        Collections.addAll(resultList, array1);
        Collections.addAll(resultList, array2);

        @SuppressWarnings("unchecked")
        // The type cast is safe as the array1 has the type T[]
        T[] resultArray = (T[]) Array.newInstance(array1.getClass().getComponentType(), 0);
        return resultList.toArray(resultArray);
    }

    public static Model combineModels(String name, Model model0, Model model1) {
        Model model = new Model(name, new Box[]{});

        model.name = model0.name + model1.name.replace(name, "");

        if (model0.boxes == null) model0.boxes = new Box[]{};
        if (model1.boxes == null) model1.boxes = new Box[]{};

        model.boxes = concatenateWithCollection(model0.boxes, model1.boxes);

        return model;
    }

    public static Parent getParentFromName(Set<Parent> parents, String name) {
        for (Parent parent : parents) {
            if (parent.model.name.equals(name)) return parent;
        }
        return null;
    }

    private static List<Integer> encodeBlockModel(Model model) {
        final int maxInt8 = 255;

        List<Integer> data = new ArrayList<>();

        // Box rotation

        int boxCount = model.boxes.length;

        data.add(boxCount); data.add(0); data.add(0); data.add(0);

        for (Box box : model.boxes) {

            // Size

            int sizeX = (int) (box.size[0] * maxInt8);
            int sizeY = (int) (box.size[1] * maxInt8);
            int sizeZ = (int) (box.size[2] * maxInt8);

            data.add(sizeX); data.add(sizeY); data.add(sizeZ); data.add(0);

            // Offset

            int offsetX = (int) ((box.offset[0] * 0.5 + 0.5) * maxInt8);
            int offsetY = (int) ((box.offset[1] * 0.5 + 0.5) * maxInt8);
            int offsetZ = (int) ((box.offset[2] * 0.5 + 0.5) * maxInt8);

            data.add(offsetX); data.add(offsetY); data.add(offsetZ); data.add(0);

            // Model rotation

            int modelRotationX = box.modelRotation[0].intValue() * maxInt8 / 270;
            int modelRotationY = box.modelRotation[1].intValue() * maxInt8 / 270;
            int modelRotationZ = box.uvLock;

            data.add((modelRotationX)); data.add(modelRotationY); data.add(modelRotationZ); data.add(0);

            // Pivot

            int pivotX = (int) ((box.pivot[0] * 0.5 + 0.5) * maxInt8);
            int pivotY = (int) ((box.pivot[1] * 0.5 + 0.5) * maxInt8);
            int pivotZ = (int) ((box.pivot[2] * 0.5 + 0.5) * maxInt8);

            data.add(pivotX); data.add(pivotY); data.add(pivotZ); data.add(0);

            // Box rotation

            int boxRotationX = (box.boxRotation[0].intValue() + 90) * maxInt8 / 180;
            int boxRotationY = (box.boxRotation[1].intValue() + 90) * maxInt8 / 180;
            int boxRotationZ = (box.boxRotation[2].intValue() + 90) * maxInt8 / 180;

            data.add(boxRotationX); data.add(boxRotationY); data.add(boxRotationZ); data.add(0);
        }

        return data;
    }

    private static Set<Parent> seedFluidBlocks() {
        Set<Parent> individualBlocks = new HashSet<>();

        List<Model> waterChildren = new ArrayList<>();
        List<Model> lavaChildren  = new ArrayList<>();

        lavaModel.boxes[0].uvLock = 1;

        waterChildren.add(waterModel);
        lavaChildren.add(lavaModel);

        individualBlocks.add(new Parent(waterModel, waterChildren));
        individualBlocks.add(new Parent(lavaModel, lavaChildren));

        return individualBlocks;
    }

    private static List<Set<Map.Entry<String, JsonElement>>> extractConditionSets(JsonObject blockState) {
        List<Set<Map.Entry<String, JsonElement>>> conditionSets = new ArrayList<>();

        JsonElement whenElement = blockState.get("when");

        if (whenElement == null) {
            conditionSets.add(Collections.emptySet());
            return conditionSets;
        }

        JsonObject when = whenElement.getAsJsonObject();

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

        return conditionSets;
    }

    // Renders one condition set to the ":key=value" suffix used in a case's model name
    private static String buildConditionSuffix(Set<Map.Entry<String, JsonElement>> conditionSet) {
        StringBuilder conditionBuilder = new StringBuilder();

        for (Map.Entry<String, JsonElement> condition : conditionSet) {
            if (conditionSet.isEmpty()) break;

            String value = condition.getValue().getAsString();
            conditionBuilder.append(":").append(condition.getKey()).append("=").append(value);
        }

        return conditionBuilder.toString();
    }

    private static Set<Parent> parseMultipartCases(File blockStatesFile, JsonElement multipartArray) {
        Set<Parent> cases = new HashSet<>();

        for (int i = 0; i < multipartArray.getAsJsonArray().size(); i++) {

            JsonObject blockState = multipartArray.getAsJsonArray().get(i).getAsJsonObject();

            JsonElement apply = blockState.get("apply");

            List<Set<Map.Entry<String, JsonElement>>> conditionSets = extractConditionSets(blockState);

            for (Set<Map.Entry<String, JsonElement>> conditionSet : conditionSets) {
                String conditionSuffix = buildConditionSuffix(conditionSet);

                Parent parent     = findModelParent(blockStatesFile, conditionSuffix, apply);
                parent.model.name = blockStatesFile.getName().replace(".json", "") + conditionSuffix;
                parent.model      = new Multipart(parent.model.name, parent.model.boxes);

                cases.add(parent);
            }
        }

        return cases;
    }

    private static void parseVariants(File blockStatesFile, JsonElement variantsElement, List<Parent> parents) {
        JsonObject blockStates = variantsElement.getAsJsonObject();

        for (Map.Entry<String, JsonElement> blockState : blockStates.entrySet()) {
            Parent parent = findModelParent(blockStatesFile, blockState.getKey(), blockState.getValue());
            parents.add(parent);
        }
    }

    private static final class BlockStateScanResult {
        final List<Parent> parents = new ArrayList<>();
        final List<Set<Parent>> multipartCaseSets = new ArrayList<>();
    }

    private static BlockStateScanResult scanBlockStates() throws IOException {
        BlockStateScanResult result = new BlockStateScanResult();

        File blockStatesFolder  = new File(propertiesPath);
        File[] blockStatesFiles = blockStatesFolder.listFiles();

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
                result.multipartCaseSets.add(parseMultipartCases(blockStatesFile, objects));
            } else {
                parseVariants(blockStatesFile, objects, result.parents);
            }

            reader.close();
        }

        return result;
    }

    private static void promoteIndividualBlocks(List<Parent> parents, Set<Parent> individualBlocks) {
        List<String> individualNames = Arrays.asList(IndividualBlocksList.individualBlocksList.split("\n"));

        for (Parent parent : parents) {
            for (Model model : parent.children) {

                String baseName = model.name.contains(":")
                    ? model.name.substring(0, model.name.indexOf(":"))
                    : model.name;

                if (individualNames.contains(baseName)) {
                    List<Model> children = new ArrayList<>();
                    children.add(model);

                    model.boxes = parent.model.boxes;

                    individualBlocks.add(new Parent(new Multipart(model.name, model.boxes), children));
                }
            }
        }
    }

    private static final class CaseAxes {
        Map<String, Set<String>> valuesByCondition = new HashMap<>();
        Parent outsider  = null;
        String blockName = "";
    }

    private static CaseAxes collectCaseAxes(Set<Parent> set) {
        CaseAxes axes = new CaseAxes();
        Set<String> uniqueCases = new HashSet<>();

        for (Parent parent : set) {
            axes.blockName = parent.model.name;

            if (!parent.model.name.contains(":")) {
                axes.outsider = parent;
                continue;
            }

            String[] subName = parent.model.name.split(":");
            axes.blockName = subName[0];

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

                    if (!case1.equals(case0)) continue;

                    if (val.contains("|")) {
                        String[] subVal = val.split("\\|");
                        vals.addAll(Arrays.asList(subVal));
                        continue;
                    }

                    vals.add(val);

                    // Hardcoded cases

                    if (axes.blockName.contains("wall")) {
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

            axes.valuesByCondition.put(case0, vals);
        }

        return axes;
    }

    private static List<List<Parent>> computeCaseCombinations(Set<Parent> set, CaseAxes axes) {
        int totalPossibleCombinations = 1;

        for (Set<String> vals : axes.valuesByCondition.values()) {
            totalPossibleCombinations *= vals.size();
        }

        List<List<Parent>> allCombinations = new ArrayList<>();

        int counter = 0;
        while (counter < totalPossibleCombinations) {
            int copy = counter;
            List<Parent> combination = new ArrayList<>();

            for (Map.Entry<String, Set<String>> conditionCase : axes.valuesByCondition.entrySet()) {
                List<String> arr = new ArrayList<>(conditionCase.getValue());

                String name = axes.blockName + ":" + conditionCase.getKey() + "=" + arr.get(copy % arr.size());

                Parent parent = getParentFromName(set, name);

                if (axes.outsider != null) {
                    combination.add(axes.outsider);
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

        return allCombinations;
    }

    private static List<List<List<Parent>>> expandAllMultipartCombinations(List<Set<Parent>> multipartCaseSets) {
        List<List<List<Parent>>> totalCombinations = new ArrayList<>();

        for (Set<Parent> set : multipartCaseSets) {
            CaseAxes axes = collectCaseAxes(set);
            totalCombinations.add(computeCaseCombinations(set, axes));
        }

        return totalCombinations;
    }

    private static void mergeCombinationsIntoParents(List<List<List<Parent>>> totalCombinations, List<Parent> parents) {

        for (List<List<Parent>> modelCombinations : totalCombinations) {
            for (List<Parent> combination : modelCombinations) {

                Model model = new Model("", new Box[]{});
                String blockName = "";

                for (Parent parent : combination) {
                    // The canonical block name is anything before the first ":" symbol
                    blockName = parent.model.name.contains(":")
                        ? parent.model.name.substring(0, parent.model.name.indexOf(":"))
                        : parent.model.name;

                    model = combineModels(blockName, model, parent.model);
                }

                model.name = blockName + model.name;

                parents.add(new Parent(model, new ArrayList<>()));
            }
        }
    }

    private static Set<Parent> removeDuplicateParents(List<Parent> parents) {
        Set<Parent> parentsNoDuplicates = new HashSet<>();

        for (Parent parent : parents) {
            List<Model> children = new ArrayList<>();

            for (Parent duplicate : parents) {
                if (!parent.model.equals(duplicate.model)) continue;

                if (duplicate.children.isEmpty())
                    children.add(duplicate.model);
                else
                    children.add(duplicate.children.get(0));
            }

            if (parent.model.boxes != null) {
                parentsNoDuplicates.add(new Parent(parent.model, children));
            }
        }

        return parentsNoDuplicates;
    }

    private static List<Parent> buildSortedParentList(List<Parent> parents, Set<Parent> individualBlocks) {
        Set<Parent> deduplicated = removeDuplicateParents(parents);

        List<Parent> sorted = new ArrayList<>(deduplicated.stream().sorted().toList());
        sorted.addAll(individualBlocks.stream().sorted().toList());

        return sorted;
    }

    private static boolean isFullBlock(Model model) {
        if (model.boxes == null || model.boxes.length != 1) return false;

        Box box = model.boxes[0];

        final Box fullBlock = new Box(
            new Double[]{0.5, 0.5, 0.5},    // size
            new Double[]{0.0, 0.0, 0.0},    // offset
            new Double[]{0.0, 0.0},         // model rotation
            new Double[]{0.0, 0.0, 0.0},    // box rotation
            new Double[]{0.0, 0.0, 0.0},    // pivot
            1                               // uv lock
        );

        return Objects.equals(box, fullBlock);
    }

    // Formats "some_block:key=val" into the UPPER_SNAKE_CASE macro name used in the block id mappings file
    private static String toMacroName(String modelName) {
        return modelName
            .replace(":", "_")
            .replace("=", "_")
            .toUpperCase();
    }

    private static void appendIdMapping(StringBuilder idMappings, String macroName, int id) {
        String defineString = "#define " + macroName;

        idMappings
            .append(defineString)
            .append(
                " ".repeat(Math.max(0, MAX_ID_MAPPING_LINE_WIDTH - defineString.length()))
            )
            .append(id).append("\n");
    }

    private static final class WriteResult {
        int maxBoxes;
        int parentCount;
        int totalByteCount;
    }

    private static WriteResult writeOutputFiles(List<Parent> parentsSorted) throws IOException {
        StringBuilder properties       = new StringBuilder();
        FileWriter    propertiesWriter = new FileWriter(propertiesOutPath);

        properties
            .append("# Generated block ID mappings (https://github.com/BelmuTM/BlockModelParser)")
            .append("\n\n");

        StringBuilder idMappings       = new StringBuilder();
        FileWriter    idMappingsWriter = new FileWriter(idMappingsOutPath);

        idMappings
            .append("// Generated block ID mappings (https://github.com/BelmuTM/BlockModelParser)")
            .append("\n\n");

        List<List<Integer>> totalModelData = new ArrayList<>();

        List<String> individualNames = Arrays.asList(IndividualBlocksList.individualBlocksList.split("\n"));

        int id = 0;
        int maxBoxes = -1;

        for (Parent parent : parentsSorted) {
            Set<Box> boxNoDuplicates = new HashSet<>(Arrays.asList(parent.model.boxes));

            parent.model.boxes = boxNoDuplicates.toArray(new Box[0]);

            maxBoxes = Math.max(parent.model.boxes.length, maxBoxes);
            id++;

            StringBuilder childrenList = new StringBuilder();

            for (Model child : parent.children) {
                String baseName = child.name.contains(":")
                    ? child.name.substring(0, child.name.indexOf(":"))
                    : child.name;

                if (individualNames.contains(baseName) && !parent.model.name.equals(child.name))
                    continue;

                childrenList.append(child.name).append(" ");
            }

            // Populating block_id_mappings.glsl file

            String blockName = parent.children.isEmpty()
                ? parent.model.name
                : parent.children.get(0).name;

            if (blockName.contains(":"))
                blockName = blockName.substring(0, blockName.indexOf(":"));

            boolean fullBlock = isFullBlock(parent.model);

            if (individualNames.contains(blockName)) {
                appendIdMapping(idMappings, toMacroName(parent.children.get(0).name), id);

            } else if (fullBlock && parent.children.size() > 1) {
                appendIdMapping(idMappings, "FULL_BLOCKS", id);
            }

            // Populating block.properties file
            properties
                .append("block.")
                .append(id)
                .append(" = ")
                .append(childrenList.toString().trim())
                .append("\n");

            totalModelData.add(encodeBlockModel(parent.model));
        }

        FileOutputStream outputStream     = new FileOutputStream(modelDataPath);
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        final int pixelsPerBox = 5;
        int maxPixels = (maxBoxes * pixelsPerBox) + 1;

        final int bytesPerPixel = 4;

        int totalByteCount = 0;

        for (List<Integer> modelData : totalModelData) {
            for (Integer byteValue : modelData) {
                dataOutputStream.writeByte(byteValue);
                totalByteCount++;
            }

            int padding = ((maxPixels * bytesPerPixel) - modelData.size()) + bytesPerPixel;

            for (int i = 0; i < padding; i++) {
                dataOutputStream.writeByte(0);
                totalByteCount++;
            }
        }

        dataOutputStream.close();

        propertiesWriter.write(properties.toString());
        propertiesWriter.close();

        idMappingsWriter.write(idMappings.toString());
        idMappingsWriter.close();

        WriteResult result = new WriteResult();
        result.maxBoxes       = maxPixels + 1;
        result.parentCount    = parentsSorted.size();
        result.totalByteCount = totalByteCount;

        return result;
    }

    public static void generateBlockFiles() {
        try {
            long processStart = System.currentTimeMillis();

            Set<Parent> individualBlocks = seedFluidBlocks();

            BlockStateScanResult scanResult = scanBlockStates();
            List<Parent> parents = scanResult.parents;

            promoteIndividualBlocks(parents, individualBlocks);

            List<List<List<Parent>>> totalCombinations = expandAllMultipartCombinations(scanResult.multipartCaseSets);

            mergeCombinationsIntoParents(totalCombinations, parents);

            List<Parent> parentsSorted = buildSortedParentList(parents, individualBlocks);

            WriteResult writeResult = writeOutputFiles(parentsSorted);

            System.out.println("[INFO] Image resolution: " + writeResult.maxBoxes + " x " + writeResult.parentCount);
            System.out.println("[INFO] Image size: " + writeResult.totalByteCount + " bytes");

            long processEnd = System.currentTimeMillis();
            System.out.println("[SUCCESS] Wrote to files in " + (processEnd - processStart) + "ms.");

        } catch(IOException ioe) {
            System.out.println("[ERROR]: Failed to write output data to files: " + ioe.getMessage());
        }
    }

    public static void main(String[] args) {
        generateBlockFiles();
    }

}
