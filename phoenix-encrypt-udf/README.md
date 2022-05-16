Phoenix-enc/dec-udf
==============

AES encryption and decryption for Apache Phoenix

Installation
------------

0. To use UDFs, you have to add the following property to `hbase-site.xml` on both client and server.

   ```xml
   <property>
    <name>phoenix.functions.allowUserDefinedFunctions</name>
    <value>true</value>
    <name>hbase.dynamic.jars.dir</name>
    <value>hdfs://<hdfs fqn>:<port>/hbase/lib</value>
    <description>
      The directory from which the custom udf jars can be loaded
      dynamically by the phoenix client/region server without the need to restart. However,
      an already loaded udf class would not be un-loaded. See
      HBASE-1936 for more details.
    </description>
    </property>
   </property>
   ```

1. Build a UDF jar and copy it into your `${hbase.dynamic.jars.dir}`.

   ```sh
   mvn clean package
   # adjust /hbase/lib to your ${hbase.dynamic.jars.dir}
   sudo -u hbase hadoop fs -copyFromLocal target/phoenix-encrypt-udf-1.0.2.jar /hbase/lib
   ```

2. Run CREATE FUNCTION.

   ```sql
   -- for encryption
   CREATE FUNCTION encrypt(VARCHAR, VARCHAR, VARCHAR CONSTANT DEFAULTVALUE='AES/CBC/PKCS5Padding') RETURNS VARCHAR AS 'com.bigdata.hbase.phoenix.Encrypt';

   -- for decryption
   CREATE FUNCTION decrypt(VARCHAR, VARCHAR, VARCHAR CONSTANT DEFAULTVALUE='AES/CBC/PKCS5Padding') RETURNS VARCHAR AS 'com.bigdata.hbase.phoenix.Decrypt';
   ```

Refer to [User-defined functions (UDFs)](https://phoenix.apache.org/udf.html) official documentation for general information about UDFs.

Usage
-----

```sql
encrypt(colname, key, algo)
```

### Example

1. Creating simple table with unencrypted rows

   ```sql
   > CREATE TABLE foo (id INTEGER NOT NULL, val VARCHAR, CONSTRAINT pk PRIMARY KEY (id));
   > UPSERT INTO foo (id, val) VALUES (1, "foo");
   > UPSERT INTO foo (id, val) VALUES (2, "bar");
   ```
   ```sql
   > SELECT * FROM foo;
    +-----+------+
    | ID  | VAL  |
    +-----+------+
    | 1   | foo  |
    | 2   | bar  |
    +-----+------+

   > SELECT id, encrypt(val, 'n9Tp9+69gxNdUg9F632u1cCRuqcOuGmN', 'AES/CBC/PKCS5Padding') as enc FROM foo;
    +-----+---------------------------+
    | ID  |            ENC            |
    +-----+---------------------------+
    | 1   | GGswrm1U5QJCinfm8QbUnQ==  |
    | 2   | tqQpcx7EeQ5B1RkHe9d4dA==  |
    +-----+---------------------------+
   ```

2. Simple table with encrypted rows

   ```sql
   > CREATE TABLE foo (id INTEGER NOT NULL, val VARCHAR, CONSTRAINT pk PRIMARY KEY (id));
   > UPSERT INTO foo (id, val) VALUES (1, "tqQpcx7EeQ5B1RkHe9d4dA==");
   > UPSERT INTO foo (id, val) VALUES (2, "b2vgwX61osfSwv/pMEBQzg==");
   ```
   ```sql
   > SELECT * FROM foo;
    +-----+---------------------------+
    | ID  |            VAL            |
    +-----+---------------------------+
    | 1   | tqQpcx7EeQ5B1RkHe9d4dA==  |
    | 2   | b2vgwX61osfSwv/pMEBQzg==  |
    +-----+---------------------------+

   > SELECT id, decrypt(val, 'n9Tp9+69gxNdUg9F632u1cCRuqcOuGmN', 'AES/CBC/PKCS5Padding') as dec FROM foo;
    +-----+------------+
    | ID  |    DEC     |
    +-----+------------+
    | 1   | bar        |
    | 2   | 123456789  |
    +-----+------------+

   ```

License
-------

[The Apache License, Version 2.0](LICENSE)