package hgloot.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FakePcsDiveGltfImporter {
    enum Part {
        HEAD("Head"),
        BODY("Body"),
        RIGHT_ARM("Right Arm"),
        LEFT_ARM("Left Arm"),
        RIGHT_LEG("Right Leg"),
        LEFT_LEG("Left Leg");

        private final String nodeName;

        Part(String nodeName) {
            this.nodeName = nodeName;
        }

        String nodeName() {
            return nodeName;
        }
    }

    record Animation(List<Pose> poses, List<Double> keyTimesSeconds) {
    }

    record Pose(EnumMap<Part, PartTransform> partTransforms) {
        PartTransform partTransform(Part part) {
            return partTransforms.get(part);
        }
    }

    record PartTransform(Vector3f translation, Quaternionf rotation) {
    }

    private record NodeData(String name, Vector3f translation, Quaternionf rotation, int[] children) {
    }

    private record BufferViewData(int bufferIndex, int byteOffset, int byteLength, int byteStride) {
    }

    private record AccessorData(int bufferViewIndex, int byteOffset, int count, int componentType, String type) {
    }

    private record GlobalTransform(Vector3f translation, Quaternionf rotation) {
    }

    private FakePcsDiveGltfImporter() {
    }

    static Animation load(Path gltfPath) throws IOException {
        String rawJson = Files.readString(gltfPath, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        List<NodeData> nodes = parseNodes(root.getAsJsonArray("nodes"));
        int[] parents = buildParentIndex(nodes);
        byte[][] buffers = loadBuffers(gltfPath, root.getAsJsonArray("buffers"));
        List<BufferViewData> bufferViews = parseBufferViews(root.getAsJsonArray("bufferViews"));
        List<AccessorData> accessors = parseAccessors(root.getAsJsonArray("accessors"));
        AnimationChannels channels = parseAnimationChannels(root, buffers, bufferViews, accessors);
        EnumMap<Part, Integer> partNodeIndexes = resolvePartNodeIndexes(nodes);
        List<Pose> poses = buildPoses(nodes, parents, channels.rotationsByNode(), partNodeIndexes, channels.poseCount());
        if (poses.isEmpty()) {
            throw new JsonParseException("No usable poses were imported from " + gltfPath.getFileName());
        }
        return new Animation(poses, channels.keyTimesSeconds());
    }

    private static List<NodeData> parseNodes(JsonArray nodesJson) {
        List<NodeData> nodes = new ArrayList<>();
        if (nodesJson == null) {
            return nodes;
        }
        for (JsonElement element : nodesJson) {
            JsonObject node = element.getAsJsonObject();
            nodes.add(new NodeData(
                node.has("name") ? node.get("name").getAsString() : "",
                readVector3(node.getAsJsonArray("translation"), new Vector3f()),
                readQuaternion(node.getAsJsonArray("rotation"), new Quaternionf()),
                readIntArray(node.getAsJsonArray("children"))
            ));
        }
        return nodes;
    }

    private static int[] buildParentIndex(List<NodeData> nodes) {
        int[] parents = new int[nodes.size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = -1;
        }
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            for (int childIndex : nodes.get(nodeIndex).children()) {
                if (childIndex >= 0 && childIndex < parents.length) {
                    parents[childIndex] = nodeIndex;
                }
            }
        }
        return parents;
    }

    private static byte[][] loadBuffers(Path gltfPath, JsonArray buffersJson) throws IOException {
        if (buffersJson == null || buffersJson.isEmpty()) {
            throw new JsonParseException("glTF does not contain any buffers.");
        }
        byte[][] buffers = new byte[buffersJson.size()][];
        for (int i = 0; i < buffersJson.size(); i++) {
            JsonObject buffer = buffersJson.get(i).getAsJsonObject();
            String uri = buffer.has("uri") ? buffer.get("uri").getAsString() : "";
            if (uri.startsWith("data:") && uri.contains(";base64,")) {
                String encoded = uri.substring(uri.indexOf(",") + 1);
                buffers[i] = Base64.getDecoder().decode(encoded);
                continue;
            }
            if (uri.isBlank()) {
                throw new JsonParseException("Unsupported buffer without URI.");
            }
            Path externalBuffer = gltfPath.getParent() == null
                ? Path.of(uri).normalize()
                : gltfPath.getParent().resolve(uri).normalize();
            buffers[i] = Files.readAllBytes(externalBuffer);
        }
        return buffers;
    }

    private static List<BufferViewData> parseBufferViews(JsonArray bufferViewsJson) {
        List<BufferViewData> bufferViews = new ArrayList<>();
        if (bufferViewsJson == null) {
            return bufferViews;
        }
        for (JsonElement element : bufferViewsJson) {
            JsonObject bufferView = element.getAsJsonObject();
            bufferViews.add(new BufferViewData(
                bufferView.get("buffer").getAsInt(),
                bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0,
                bufferView.get("byteLength").getAsInt(),
                bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : 0
            ));
        }
        return bufferViews;
    }

    private static List<AccessorData> parseAccessors(JsonArray accessorsJson) {
        List<AccessorData> accessors = new ArrayList<>();
        if (accessorsJson == null) {
            return accessors;
        }
        for (JsonElement element : accessorsJson) {
            JsonObject accessor = element.getAsJsonObject();
            accessors.add(new AccessorData(
                accessor.get("bufferView").getAsInt(),
                accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0,
                accessor.get("count").getAsInt(),
                accessor.get("componentType").getAsInt(),
                accessor.get("type").getAsString()
            ));
        }
        return accessors;
    }

    private static AnimationChannels parseAnimationChannels(
        JsonObject root,
        byte[][] buffers,
        List<BufferViewData> bufferViews,
        List<AccessorData> accessors
    ) {
        JsonArray animationsJson = root.getAsJsonArray("animations");
        if (animationsJson == null || animationsJson.isEmpty()) {
            throw new JsonParseException("glTF does not contain any animations.");
        }
        JsonObject animation = animationsJson.get(0).getAsJsonObject();
        JsonArray samplersJson = animation.getAsJsonArray("samplers");
        JsonArray channelsJson = animation.getAsJsonArray("channels");
        if (samplersJson == null || channelsJson == null) {
            throw new JsonParseException("glTF animation is missing samplers or channels.");
        }

        Map<Integer, Quaternionf[]> rotationsByNode = new HashMap<>();
        List<Double> keyTimesSeconds = List.of();
        for (JsonElement channelElement : channelsJson) {
            JsonObject channel = channelElement.getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            if (target == null || !target.has("path") || !"rotation".equals(target.get("path").getAsString())) {
                continue;
            }
            int samplerIndex = channel.get("sampler").getAsInt();
            JsonObject sampler = samplersJson.get(samplerIndex).getAsJsonObject();
            double[] inputTimes = readScalarAccessor(buffers, bufferViews, accessors, sampler.get("input").getAsInt());
            Quaternionf[] outputRotations = readVec4QuaternionAccessor(
                buffers,
                bufferViews,
                accessors,
                sampler.get("output").getAsInt()
            );
            if (keyTimesSeconds.isEmpty()) {
                keyTimesSeconds = toList(inputTimes);
            }
            rotationsByNode.put(target.get("node").getAsInt(), outputRotations);
        }
        if (rotationsByNode.isEmpty()) {
            throw new JsonParseException("glTF animation does not contain any rotation channels.");
        }
        return new AnimationChannels(rotationsByNode, keyTimesSeconds);
    }

    private static EnumMap<Part, Integer> resolvePartNodeIndexes(List<NodeData> nodes) {
        EnumMap<Part, Integer> indexes = new EnumMap<>(Part.class);
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NodeData node = nodes.get(nodeIndex);
            if (node.children().length == 0 || node.name() == null || node.name().isBlank()) {
                continue;
            }
            for (Part part : Part.values()) {
                if (part.nodeName().equals(node.name()) && !indexes.containsKey(part)) {
                    indexes.put(part, nodeIndex);
                    break;
                }
            }
        }
        if (indexes.size() != Part.values().length) {
            throw new JsonParseException("glTF rig is missing one or more expected body parts.");
        }
        return indexes;
    }

    private static List<Pose> buildPoses(
        List<NodeData> nodes,
        int[] parents,
        Map<Integer, Quaternionf[]> rotationsByNode,
        EnumMap<Part, Integer> partNodeIndexes,
        int poseCount
    ) {
        List<Pose> poses = new ArrayList<>();
        for (int poseIndex = 0; poseIndex < poseCount; poseIndex++) {
            Map<Integer, GlobalTransform> transformCache = new HashMap<>();
            EnumMap<Part, PartTransform> partTransforms = new EnumMap<>(Part.class);
            for (Map.Entry<Part, Integer> entry : partNodeIndexes.entrySet()) {
                GlobalTransform transform = globalTransformForNode(
                    entry.getValue(),
                    poseIndex,
                    nodes,
                    parents,
                    rotationsByNode,
                    transformCache
                );
                partTransforms.put(
                    entry.getKey(),
                    new PartTransform(new Vector3f(transform.translation()), new Quaternionf(transform.rotation()))
                );
            }
            poses.add(new Pose(partTransforms));
        }
        return poses;
    }

    private static GlobalTransform globalTransformForNode(
        int nodeIndex,
        int poseIndex,
        List<NodeData> nodes,
        int[] parents,
        Map<Integer, Quaternionf[]> rotationsByNode,
        Map<Integer, GlobalTransform> transformCache
    ) {
        GlobalTransform cached = transformCache.get(nodeIndex);
        if (cached != null) {
            return cached;
        }
        NodeData node = nodes.get(nodeIndex);
        Quaternionf localRotation = localRotationForPose(nodeIndex, poseIndex, node.rotation(), rotationsByNode);
        Vector3f localTranslation = new Vector3f(node.translation());
        int parentIndex = parents[nodeIndex];
        GlobalTransform resolved;
        if (parentIndex < 0) {
            resolved = new GlobalTransform(localTranslation, localRotation);
        } else {
            GlobalTransform parent = globalTransformForNode(
                parentIndex,
                poseIndex,
                nodes,
                parents,
                rotationsByNode,
                transformCache
            );
            Vector3f globalTranslation = localTranslation.rotate(new Quaternionf(parent.rotation())).add(parent.translation());
            Quaternionf globalRotation = new Quaternionf(parent.rotation()).mul(localRotation);
            resolved = new GlobalTransform(globalTranslation, globalRotation);
        }
        transformCache.put(nodeIndex, resolved);
        return resolved;
    }

    private static Quaternionf localRotationForPose(
        int nodeIndex,
        int poseIndex,
        Quaternionf defaultRotation,
        Map<Integer, Quaternionf[]> rotationsByNode
    ) {
        if (poseIndex <= 0) {
            return new Quaternionf(defaultRotation);
        }
        Quaternionf[] keyedRotations = rotationsByNode.get(nodeIndex);
        if (keyedRotations == null || keyedRotations.length == 0) {
            return new Quaternionf(defaultRotation);
        }
        int keyedIndex = Math.min(poseIndex - 1, keyedRotations.length - 1);
        return new Quaternionf(keyedRotations[keyedIndex]);
    }

    private static double[] readScalarAccessor(
        byte[][] buffers,
        List<BufferViewData> bufferViews,
        List<AccessorData> accessors,
        int accessorIndex
    ) {
        AccessorData accessor = accessors.get(accessorIndex);
        if (accessor.componentType() != 5126 || !"SCALAR".equals(accessor.type())) {
            throw new JsonParseException("Unsupported scalar accessor format for animation input.");
        }
        BufferViewData bufferView = bufferViews.get(accessor.bufferViewIndex());
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffers[bufferView.bufferIndex()]).order(ByteOrder.LITTLE_ENDIAN);
        int stride = bufferView.byteStride() > 0 ? bufferView.byteStride() : Float.BYTES;
        int baseOffset = bufferView.byteOffset() + accessor.byteOffset();
        double[] values = new double[accessor.count()];
        for (int i = 0; i < accessor.count(); i++) {
            values[i] = byteBuffer.getFloat(baseOffset + (i * stride));
        }
        return values;
    }

    private static Quaternionf[] readVec4QuaternionAccessor(
        byte[][] buffers,
        List<BufferViewData> bufferViews,
        List<AccessorData> accessors,
        int accessorIndex
    ) {
        AccessorData accessor = accessors.get(accessorIndex);
        if (accessor.componentType() != 5126 || !"VEC4".equals(accessor.type())) {
            throw new JsonParseException("Unsupported quaternion accessor format for animation output.");
        }
        BufferViewData bufferView = bufferViews.get(accessor.bufferViewIndex());
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffers[bufferView.bufferIndex()]).order(ByteOrder.LITTLE_ENDIAN);
        int stride = bufferView.byteStride() > 0 ? bufferView.byteStride() : (Float.BYTES * 4);
        int baseOffset = bufferView.byteOffset() + accessor.byteOffset();
        Quaternionf[] quaternions = new Quaternionf[accessor.count()];
        for (int i = 0; i < accessor.count(); i++) {
            int offset = baseOffset + (i * stride);
            quaternions[i] = new Quaternionf(
                byteBuffer.getFloat(offset),
                byteBuffer.getFloat(offset + Float.BYTES),
                byteBuffer.getFloat(offset + (Float.BYTES * 2)),
                byteBuffer.getFloat(offset + (Float.BYTES * 3))
            ).normalize();
        }
        return quaternions;
    }

    private static Vector3f readVector3(JsonArray array, Vector3f fallback) {
        if (array == null || array.size() < 3) {
            return new Vector3f(fallback);
        }
        return new Vector3f(
            array.get(0).getAsFloat(),
            array.get(1).getAsFloat(),
            array.get(2).getAsFloat()
        );
    }

    private static Quaternionf readQuaternion(JsonArray array, Quaternionf fallback) {
        if (array == null || array.size() < 4) {
            return new Quaternionf(fallback);
        }
        return new Quaternionf(
            array.get(0).getAsFloat(),
            array.get(1).getAsFloat(),
            array.get(2).getAsFloat(),
            array.get(3).getAsFloat()
        ).normalize();
    }

    private static int[] readIntArray(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return new int[0];
        }
        int[] out = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add(value);
        }
        return out;
    }

    private record AnimationChannels(Map<Integer, Quaternionf[]> rotationsByNode, List<Double> keyTimesSeconds) {
        int poseCount() {
            return keyTimesSeconds.size() + 1;
        }
    }
}
