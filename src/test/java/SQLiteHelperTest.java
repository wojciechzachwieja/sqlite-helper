import org.junit.After;
import org.junit.Before;

import java.sql.Connection;

public class SQLiteHelperTest {

    private static final String URL_TO_DATABASE = "jdbc:sqlite:identifier.sqlite";
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        connection = SqliteUtils.open(URL_TO_DATABASE);
    }

    @After
    public void tearDown() throws Exception {
        SqliteUtils.close(connection);
    }

    @org.junit.Test
    public void createTable() throws Exception {
        //given
        SQLiteHelper sqLiteHelper = new SQLiteHelper();
        TestObjects.Test1 object = new TestObjects.Test1();

        //when
        SqliteUtils.execute(connection, sqLiteHelper.createTable(object));
    }


    @org.junit.Test
    public void createTable2() throws Exception {
        //given
        SQLiteHelper sqLiteHelper = new SQLiteHelper();
        TestObjects.Test2 object = new TestObjects.Test2();

        //when
        SqliteUtils.execute(connection, sqLiteHelper.createTable(object));
    }

    @org.junit.Test
    public void insert() throws Exception {
        //given
        SQLiteHelper sqLiteHelper = new SQLiteHelper();
        TestObjects.Test1 object = new TestObjects.Test1();

        //when
        SqliteUtils.execute(connection, sqLiteHelper.insert(object));
    }


    @org.junit.Test
    public void insert2() {
        //given
        SQLiteHelper sqLiteHelper = new SQLiteHelper();
        TestObjects.Test1 object = new TestObjects.Test1();
        object.aBoolean = true;
        object.aBoolean2 = false;
        object.string = "'";
        object.aFloat = 123.12f;
        object.aDouble = 321.0;
        object.aLong = 2L;

        //when
        SqliteUtils.execute(connection, sqLiteHelper.insert(object));
    }
}