package org.capnproto;

import org.scalatest.FunSuite;
import org.scalatest.Matchers._;
import java.nio.ByteBuffer;

class SerializePackedSuite extends FunSuite {

  def expectPacksTo(unpacked : Array[Byte], packed : Array[Byte]) {
    // ----
    // write

    val bytes = new Array[Byte](packed.length);
    val writer = new ArrayOutputStream(ByteBuffer.wrap(bytes));
    val packedOutputStream = new PackedOutputStream (writer);
    packedOutputStream.write(ByteBuffer.wrap(unpacked));

    (bytes) should equal (packed);

  }

  test("SimplePacking") {
    expectPacksTo(Array(), Array());
    expectPacksTo(Array(0,0,12,0,0,34,0,0), Array(0x24,12,34));
    expectPacksTo(Array(1,3,2,4,5,7,6,8), Array(0xff.toByte,1,3,2,4,5,7,6,8,0));
    expectPacksTo(Array(0,0,0,0,0,0,0,0, 1,3,2,4,5,7,6,8),
                  Array(0,0,0xff.toByte,1,3,2,4,5,7,6,8,0));
    expectPacksTo(Array(0,0,12,0,0,34,0,0, 1,3,2,4,5,7,6,8),
                  Array(0x24, 12, 34, 0xff.toByte,1,3,2,4,5,7,6,8));
    //expectPacksTo(Array(1,3,2,4,5,7,6,8, 8,6,7,4,5,2,3,1),
    //              Array(0xff.toByte,1,3,2,4,5,7,6,8,1,8,6,7,4,5,2,3,1));
  }
}
