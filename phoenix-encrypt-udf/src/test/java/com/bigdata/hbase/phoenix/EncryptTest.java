package com.bigdata.hbase.phoenix;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.LiteralExpression;
import org.apache.phoenix.schema.types.PVarchar;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EncryptTest {

    private static String evaluate(final Expression expr) {
        final ImmutableBytesWritable ptr = new ImmutableBytesWritable();
        assertTrue(expr.evaluate(null, ptr));
        return (String) expr.getDataType().toObject(ptr);
    }

    @Test
    public void testEncrypt() throws Exception {
        final LiteralExpression value = LiteralExpression.newConstant("123456789", PVarchar.INSTANCE);
        final LiteralExpression key = LiteralExpression.newConstant("n9Tp9+69gxNdUg9F632u1cCRuqcOuGmN", PVarchar.INSTANCE);
        final LiteralExpression algo = LiteralExpression.newConstant("AES/CBC/PKCS5Padding", PVarchar.INSTANCE);
        assertEquals("b2vgwX61osfSwv/pMEBQzg==", evaluate(new Encrypt(Arrays.asList(value, key, algo))));
    }
}
