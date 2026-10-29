package com.g4vrk.reactdataset;

import com.g4vrk.react.inference.InferenceModel;
import com.g4vrk.react.vector.model.FrameHistory;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.protocol.FrameCodec;
import com.g4vrk.reactdataset.model.RecordLabel;
import com.g4vrk.reactdataset.notification.RecordingNotifier;
import com.g4vrk.reactdataset.session.RecordingSession;
import com.g4vrk.reactdataset.storage.CsvDatasetStorage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class UniverseParityTest {
    @TempDir
    Path directory;

    @Test
    void savedCollectorRowsAndSlidingRequestsContainIdenticalFloat32Values() throws Exception {
        CsvDatasetStorage storage = new CsvDatasetStorage(
                LoggerFactory.getLogger(UniverseParityTest.class), directory, 64);
        RecordingSession recording = recording(128, RecordLabel.LEGIT);
        FrameHistory inference = new FrameHistory(64);
        List<String[]> csvRows = new ArrayList<>();
        List<List<AimFrame>> windows = new ArrayList<>();
        for (int frameIndex = 0; frameIndex < 128; frameIndex++) {
            AimFrame frame = sample(frameIndex);
            inference.addFrame(frame);
            List<AimFrame> window = inference.pollSnapshot(32);
            if (window != null) windows.add(window);
            RecordingSession.CapturedFile batch = recording.capture(frame);
            if (batch != null) {
                assertEquals(64, batch.frames().size());
                Path csv = storage.write("player", batch.fileNumber(), recording.label(),
                        recording.source(), batch.frames());
                List<String> lines = Files.readAllLines(csv);
                String[] header = lines.get(0).split(",");
                assertEquals((AimFrame.DATASET_FEATURE_COUNT + 1), header.length);
                assertEquals("is_cheating", header[0]);
                assertEquals("delta_yaw", header[1]);
                assertEquals("held_item_class", header[AimFrame.INFERENCE_FEATURE_COUNT]);
                assertEquals("player_ping", header[AimFrame.DATASET_FEATURE_COUNT]);
                for (String line : lines.subList(1, lines.size())) csvRows.add(line.split(","));
                assertEquals(64, storage.read(csv).size());
            }
        }
        assertTrue(recording.finished());
        assertEquals(128, csvRows.size());
        assertEquals(3, windows.size()); // [0,64), [32,96), [64,128)
        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            float[] actual = decodeBatch(FrameCodec.encodeBatch(
                    windows.get(windowIndex).toArray(AimFrame[]::new)), 64);
            assertEquals(64 * AimFrame.INFERENCE_FEATURE_COUNT, actual.length);
            for (int frameIndex = 0; frameIndex < 64; frameIndex++) {
                int sourceIndex = windowIndex * 32 + frameIndex;
                String[] row = csvRows.get(sourceIndex);
                assertEquals("0", row[0]);
                assertEquals((AimFrame.DATASET_FEATURE_COUNT + 1), row.length);
                for (int featureIndex = 0; featureIndex < AimFrame.INFERENCE_FEATURE_COUNT; featureIndex++) {
                    float expected = Float.parseFloat(row[featureIndex + 1]);
                    assertEquals(Float.floatToIntBits(expected),
                            Float.floatToIntBits(actual[frameIndex * AimFrame.INFERENCE_FEATURE_COUNT + featureIndex]),
                            "frame=" + sourceIndex + ", feature=" + featureIndex);
                }
            }
        }
    }

    @Test
    void labelsAndPingStayInCsvAndNeverEnterInference() {
        float[] values = sample(0).datasetFeatures();
        AimFrame original = AimFrame.fromFeatures(values);
        values[AimFrame.INFERENCE_FEATURE_COUNT] = 9999.0f;
        AimFrame otherPing = AimFrame.fromFeatures(values);
        assertArrayEquals(FrameCodec.encodeBatch(new AimFrame[]{original}),
                FrameCodec.encodeBatch(new AimFrame[]{otherPing}));
        assertTrue(original.toCsvRow(true).startsWith("1,"));
        assertTrue(original.toCsvRow(false).startsWith("0,"));
        float[] firstRead = original.inferenceFeatures();
        firstRead[0] = 123456.0f;
        assertNotEquals(firstRead[0], original.inferenceFeatures()[0]);
        assertEquals(AimFrame.DATASET_FEATURE_COUNT, original.datasetFeatures().length);
    }

    @Test
    void combatHistoryClearPreventsFramesFromPreviousWindowBeingSent() {
        FrameHistory inference = new FrameHistory(64);
        for (int index = 0; index < 64; index++) inference.addFrame(sample(index));
        assertNotNull(inference.pollSnapshot(32));
        inference.clear();
        for (int index = 0; index < 63; index++) {
            inference.addFrame(sample(1000 + index));
            assertNull(inference.pollSnapshot(32));
        }
        inference.addFrame(sample(1063));
        List<AimFrame> window = inference.pollSnapshot(32);
        assertNotNull(window);
        for (int index = 0; index < 64; index++) {
            assertArrayEquals(sample(1000 + index).datasetFeatures(), window.get(index).datasetFeatures());
        }
    }

    private static RecordingSession recording(int frames, RecordLabel label) {
        return new RecordingSession(UUID.randomUUID(), "player", "player", label,
                "synthetic", frames, 1, 64, UUID.randomUUID(), RecordingNotifier.none(),
                0, 0, List.of(), null, InferenceModel.UNIVERSE);
    }

    private static AimFrame sample(int index) {
        float[] values = new float[AimFrame.DATASET_FEATURE_COUNT];
        for (int feature = 0; feature < values.length; feature++) {
            values[feature] = (float) (Math.sin(index * 0.23 + feature * 0.17)
                    * Math.pow(10, feature % 7 - 4));
        }
        values[0] = index + 0.12345678f;
        values[1] = -0.0000001f;
        values[AimFrame.INFERENCE_FEATURE_COUNT] = 37.0f;
        return AimFrame.fromFeatures(values);
    }

    // Decode the wire layout independently of FlatBufferBuilder and inferenceFeatures().
    private static float[] decodeBatch(byte[] bytes, int expectedFrames) {
        ByteBuffer batch = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, Short.toUnsignedInt(batch.getShort()));
        assertEquals(batch.remaining() - Integer.BYTES, batch.getInt());
        ByteBuffer payload = batch.slice().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals('G', payload.get(4));
        assertEquals('A', payload.get(5));
        assertEquals('I', payload.get(6));
        assertEquals('Q', payload.get(7));
        int table = payload.getInt(0);
        int vtable = table - payload.getInt(table);
        int frameCountField = Short.toUnsignedInt(payload.getShort(vtable + 6));
        int featuresField = Short.toUnsignedInt(payload.getShort(vtable + 8));
        int modelField = Short.toUnsignedInt(payload.getShort(vtable + 10));
        assertEquals(expectedFrames, payload.getInt(table + frameCountField));
        assertEquals(2, payload.getInt(table + modelField));
        int vectorOffset = table + featuresField;
        int vector = vectorOffset + payload.getInt(vectorOffset);
        int length = payload.getInt(vector);
        float[] values = new float[length];
        for (int index = 0; index < length; index++) {
            values[index] = payload.getFloat(vector + 4 + index * 4);
        }
        return values;
    }
}
