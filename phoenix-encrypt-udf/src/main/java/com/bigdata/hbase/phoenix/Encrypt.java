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
import org.apache.phoenix.compile.KeyPart;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.parse.FunctionParseNode.Argument;
import org.apache.phoenix.parse.FunctionParseNode.BuiltInFunction;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PVarchar;
import org.apache.phoenix.expression.function.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
// CREATE FUNCTION enc(VARCHAR, VARCHAR, VARCHAR CONSTANT DEFAULTVALUE='AES/CBC/PKCS5Padding') RETURNS VARCHAR AS 'com.bigdata.hbase.phoenix.Encrypt';
public class Encrypt extends ScalarFunction{
    private static final Logger logger = LoggerFactory.getLogger(Encrypt.class);

    public static final String NAME = "ENCRYPT";

    private String key;
    private String algo;

    static {
        // Force initializing Scope object when JsonQueryFunction is loaded using the Scope classloader. Otherwise,
        // built-in jq functions are not loaded.
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Scope.class.getClassLoader());
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
        if (!inArg.evaluate(tuple, ptr)) {
            return false;
        }

        try {
            final SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(key), "AES");
            Cipher cipher = null;
            try {
                cipher = Cipher.getInstance(algo);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                logger.error(e.toString());
            }
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(new byte[16]));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                logger.error(e.toString());
            }
            byte[] cipherText = new byte[0];
            try {
                cipherText = cipher.doFinal(ptr.copyBytes());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                logger.error(e.toString());
            }

            byte[] encrypted = Base64.getEncoder().encode(cipherText);
            ptr.set(encrypted);
            return true;
        } catch (Exception e) {
            logger.error(e.toString());
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

    /**
     * Determines whether or not a function may be used to form
     * the start/stop key of a scan
     * @return the zero-based position of the argument to traverse
     *  into to look for a primary key column reference, or
     *  {@value #NO_TRAVERSAL} if the function cannot be used to
     *  form the scan key.
     */
    @Override
    public int getKeyFormationTraversalIndex() {
        return NO_TRAVERSAL;
    }

    /**
     * Manufactures a KeyPart used to construct the KeyRange given
     * a constant and a comparison operator.
     * @param childPart the KeyPart formulated for the child expression
     *  at the {@link #getKeyFormationTraversalIndex()} position.
     * @return the KeyPart for constructing the KeyRange for this
     *  function.
     */
    @Override
    public KeyPart newKeyPart(KeyPart childPart) {
        return null;
    }

    /**
     * Determines whether or not the result of the function invocation
     * will be ordered in the same way as the input to the function.
     * Returning YES enables an optimization to occur when a
     * GROUP BY contains function invocations using the leading PK
     * column(s).
     * @return YES if the function invocation will always preserve order for
     * the inputs versus the outputs and false otherwise, YES_IF_LAST if the
     * function preserves order, but any further column reference would not
     * continue to preserve order, and NO if the function does not preserve
     * order.
     */
    @Override
    public OrderPreserving preservesOrder() {
        return OrderPreserving.NO;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
