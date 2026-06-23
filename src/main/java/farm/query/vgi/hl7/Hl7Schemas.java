package farm.query.vgi.hl7;

import farm.query.vgi.types.Schemas;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.util.List;
import java.util.Map;

/** Shared Arrow schemas + batch builders for the HL7 table functions. */
public final class Hl7Schemas {

    private Hl7Schemas() {}

    /** hl7_segments output: one row per segment, in message order. */
    public static final Schema SEGMENTS_SCHEMA = new Schema(List.of(
            commented("seq", Schemas.INT32, "1-based ordinal of the segment within the message."),
            commented("segment", Schemas.UTF8, "3-letter segment identifier (e.g. MSH, PID, PV1)."),
            commented("field_count", Schemas.INT32, "Number of fields in the segment (HL7 numbering; MSH-1 counts)."),
            commented("raw", Schemas.UTF8, "Raw text of the entire segment.")));

    /** hl7_fields output: long format, one row per field value (repetitions expanded). */
    public static final Schema FIELDS_SCHEMA = new Schema(List.of(
            commented("segment", Schemas.UTF8, "3-letter segment identifier (e.g. MSH, PID)."),
            commented("segment_rep", Schemas.INT32, "0-based occurrence index of this segment within the message."),
            commented("field", Schemas.INT32, "1-based HL7 field number within the segment."),
            commented("repetition", Schemas.INT32, "0-based repetition index of the field value."),
            commented("value", Schemas.UTF8, "Raw field-value text (component/subcomponent structure preserved).")));

    static Field commented(String name, ArrowType type, String comment) {
        return new Field(name, new FieldType(true, type, null, Map.of("comment", comment)), null);
    }

    // ---- batch builders ----------------------------------------------------

    /** A row of the segments table. */
    public record SegmentRow(int seq, String segment, int fieldCount, String raw) {}

    /** A row of the long-format fields table. */
    public record FieldRow(String segment, int segmentRep, int field, int repetition, String value) {}

    static VectorSchemaRoot segmentsBatch(BufferAllocator alloc, List<SegmentRow> rows) {
        VectorSchemaRoot root = VectorSchemaRoot.create(SEGMENTS_SCHEMA, alloc);
        root.allocateNew();
        for (int i = 0; i < rows.size(); i++) {
            SegmentRow r = rows.get(i);
            setInt(root, "seq", i, r.seq());
            setUtf8(root, "segment", i, r.segment());
            setInt(root, "field_count", i, r.fieldCount());
            setUtf8(root, "raw", i, r.raw());
        }
        root.setRowCount(rows.size());
        return root;
    }

    static VectorSchemaRoot fieldsBatch(BufferAllocator alloc, List<FieldRow> rows) {
        VectorSchemaRoot root = VectorSchemaRoot.create(FIELDS_SCHEMA, alloc);
        root.allocateNew();
        for (int i = 0; i < rows.size(); i++) {
            FieldRow r = rows.get(i);
            setUtf8(root, "segment", i, r.segment());
            setInt(root, "segment_rep", i, r.segmentRep());
            setInt(root, "field", i, r.field());
            setInt(root, "repetition", i, r.repetition());
            setUtf8(root, "value", i, r.value());
        }
        root.setRowCount(rows.size());
        return root;
    }

    static void setUtf8(VectorSchemaRoot root, String col, int row, String value) {
        VarCharVector v = (VarCharVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, new Text(value));
        }
    }

    static void setInt(VectorSchemaRoot root, String col, int row, Integer value) {
        IntVector v = (IntVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, value);
        }
    }
}
