package org.capnproto;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public final class Serialize {

    static ByteBuffer makeByteBuffer(int bytes) {
        ByteBuffer result = ByteBuffer.allocate(bytes);
        result.order(ByteOrder.LITTLE_ENDIAN);
        result.mark();
        return result;
    }

    public static void fillBuffer(ByteBuffer buffer, ReadableByteChannel bc) throws IOException {
        while(buffer.hasRemaining()) {
            int r = bc.read(buffer);
            if (r < 0) {
                throw new IOException("premature EOF");
            }
            // TODO check for r == 0 ?.
        }
    }

    public static MessageReader read(ReadableByteChannel bc) throws IOException {
        return read(bc, MessageReader.DEFAULT_TRAVERSAL_LIMIT_IN_WORDS, MessageReader.DEFAULT_NESTING_LIMIT);
    }

    public static MessageReader read(ReadableByteChannel bc, long traversalLimitInWords, int nestingLimit) throws IOException {
        ByteBuffer firstWord = makeByteBuffer(Constants.BYTES_PER_WORD);
        fillBuffer(firstWord, bc);

        int segmentCount = 1 + firstWord.getInt(0);

        int segment0Size = 0;
        if (segmentCount > 0) {
            segment0Size = firstWord.getInt(4);
        }

        int totalWords = segment0Size;

        if (segmentCount > 512) {
            throw new IOException("too many segments");
        }

        // in words
        ArrayList<Integer> moreSizes = new ArrayList<Integer>();

        if (segmentCount > 1) {
            ByteBuffer moreSizesRaw = makeByteBuffer(4 * (segmentCount & ~1));
            fillBuffer(moreSizesRaw, bc);
            for (int ii = 0; ii < segmentCount - 1; ++ii) {
                int size = moreSizesRaw.getInt(ii * 4);
                moreSizes.add(size);
                totalWords += size;
            }
        }

        // TODO check that totalWords is reasonable
        if (totalWords > traversalLimitInWords) {
            throw new DecodeException("Message size exceeds traversal limit.");
        }

        ByteBuffer allSegments = makeByteBuffer(totalWords * Constants.BYTES_PER_WORD);
        fillBuffer(allSegments, bc);

        ByteBuffer[] segmentSlices = new ByteBuffer[segmentCount];

        allSegments.rewind();
        segmentSlices[0] = allSegments.slice();
        segmentSlices[0].limit(segment0Size * Constants.BYTES_PER_WORD);
        segmentSlices[0].order(ByteOrder.LITTLE_ENDIAN);

        int offset = segment0Size;
        for (int ii = 1; ii < segmentCount; ++ii) {
            allSegments.position(offset * Constants.BYTES_PER_WORD);
            segmentSlices[ii] = allSegments.slice();
            segmentSlices[ii].limit(moreSizes.get(ii - 1) * Constants.BYTES_PER_WORD);
            segmentSlices[ii].order(ByteOrder.LITTLE_ENDIAN);
            offset += moreSizes.get(ii - 1);
        }

        return new MessageReader(segmentSlices, traversalLimitInWords, nestingLimit);
    }

    public static void write(WritableByteChannel outputChannel,
                             MessageBuilder message) throws IOException {
        ByteBuffer[] segments = message.getSegmentsForOutput();
        int tableSize = (segments.length + 2) & (~1);

        ByteBuffer table = ByteBuffer.allocate(4 * tableSize);
        table.order(ByteOrder.LITTLE_ENDIAN);

        table.putInt(0, segments.length - 1);

        for (int i = 0; i < segments.length; ++i) {
            table.putInt(4 * (i + 1), segments[i].limit() / 8);
        }

        // Any padding is already zeroed.
        while (table.hasRemaining()) {
            outputChannel.write(table);
        }

        for (ByteBuffer buffer : segments) {
            while(buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }
        }
    }
}
