package com.bigdata.hbase.phoenix;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLClassLoader;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Base64;
import java.util.List;

import net.thisptr.jackson.jq.Scope;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.parse.FunctionParseNode.Argument;
import org.apache.phoenix.parse.FunctionParseNode.BuiltInFunction;
import org.apache.phoenix.schema.ColumnModifier;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PVarchar;
import org.apache.phoenix.expression.function.*;
import org.apache.phoenix.util.StringUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@BuiltInFunction(name = Encrypt.NAME, args = {
        @Argument(allowedTypes = {PVarchar.class}), // raw data
        @Argument(allowedTypes = {PVarchar.class}), // key
        @Argument(allowedTypes = {PVarchar.class}, isConstant = true, defaultValue = "AES/CBC/PKCS5Padding"), // Encryprion algorithm
})
public class Encrypt extends ScalarFunction{

    public static final String NAME = "ENCRYPT";

    private String key;
    private String algo;

    static {
        // Force initializing Scope object when JsonQueryFunction is loaded using the Scope classloader. Otherwise,
        // built-in jq functions are not loaded.
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader((URLClassLoader) Scope.class.getClassLoader());
        try {
            Scope.rootScope();
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    public Encrypt(){}

    public Encrypt(final List<Expression> children) throws SQLException {
        super(children);
        init();
    }


    private void init() {
        final ImmutableBytesWritable key = new ImmutableBytesWritable();
        if (!getChildren().get(1).evaluate(null, key))
            throw new RuntimeException("key: the 2nd argument must be a varchar.");
        this.key = (String) PVarchar.INSTANCE.toObject(key);

        final ImmutableBytesWritable algo = new ImmutableBytesWritable();
        if (!getChildren().get(2).evaluate(null, algo)) {
            throw new RuntimeException("algorithm: the 3rd argument must be a constant varchar.");
        }

            this.algo = (String) PVarchar.INSTANCE.toObject(algo);

    }

    @Override
    public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
        final Expression inArg = getChildren().get(0);
        final ImmutableBytesWritable in = new ImmutableBytesWritable();
        if (!inArg.evaluate(tuple, in)) {
            return false;
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(key), "AES");
            Cipher cipher = null;
            try {
                cipher = Cipher.getInstance(algo);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                e.printStackTrace();
            }
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[16]));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
            byte[] cipherText = new byte[0];
            try {
                cipherText = cipher.doFinal(in.get());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                e.printStackTrace();
            }

            String encrypted = Base64.getEncoder().encodeToString(cipherText);
            ptr.set(PVarchar.INSTANCE.toBytes(encrypted));
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFields(final DataInput input) throws IOException {
        super.readFields(input);
        init();
    }

    @Override
    public PDataType<?> getDataType() {
        return PVarchar.INSTANCE;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
