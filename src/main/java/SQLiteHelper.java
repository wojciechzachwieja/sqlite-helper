import java.lang.reflect.Field;
import java.util.*;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

class SQLiteHelper implements SQLiteHelperInterface {
    private Map<String, String> indexQueries = new HashMap<>();
    private List<String> foreignQueries = new LinkedList<>();

    public String createTable(Object o) {
        String tableName = o.getClass().getName();
        return buildCreateTableQuery(o, tableName);
    }

    private String buildCreateTableQuery(Object o, String tableName) {
        StringBuilder createQuery = new StringBuilder(SQLConstants.CREATE_TABLE);
        createQuery.append(tableName)
                .append(SQLConstants.OPEN_BRACE);
        Field[] publicFields = o.getClass().getFields();

        String columns = stream(publicFields)
                .map(this::convertToColumn)
                .filter(Objects::nonNull)
                .collect(joining(SQLConstants.COMMA));

        createQuery
                .append(columns)
                .append(SQLConstants.CLOSE_QUERY);

        foreignQueries.forEach(foreignQuery -> createQuery.append(format(foreignQuery, tableName)));

        indexQueries.values()
                .forEach(indexQuery -> createQuery
                        .append(format(indexQuery, tableName))
                        .append(SQLConstants.CLOSE_QUERY));

        indexQueries.clear();
        return createQuery.toString();
    }

    private String convertToColumn(Field field) {
        Class<?> type = field.getType();
        String name = field.getName();
        String column = name + ' ';
        if (canConvertToInteger(type)) {
            KeyAnnotation keyAnnotation = field.getAnnotation(KeyAnnotation.class);
            if (keyAnnotation != null) {
                if (keyAnnotation.autoIncrement()) {
                    return column + SQLConstants.AUTOINCREMENT_PRIMARY_KEY;
                } else {
                    return column + SQLConstants.PRIMARY_KEY;
                }
            }
            ForeignKeyAnnotation foreignKeyAnnotation = field.getAnnotation(ForeignKeyAnnotation.class);
            if (foreignKeyAnnotation != null) {
                foreignQueries.add(getForeignConstraint(column, foreignKeyAnnotation));
            }
            IndexAnnotation indexAnnotation = field.getAnnotation(IndexAnnotation.class);
            if (indexAnnotation != null) {
                String indexName = indexAnnotation.indexName();
                if (indexAnnotation.isUnique()) {
                    buildIndexQuery(column, indexName, true);
                } else {
                    buildIndexQuery(column, indexName, false);
                }
            }
            return column + SQLConstants.INTEGER;
        } else if (canConvertToReal(type)) {
            return column + SQLConstants.REAL;
        } else if (canConvertToText(type)) {
            return column + SQLConstants.TEXT;
        }

        return null;
    }

    private String getForeignConstraint(String column, ForeignKeyAnnotation foreignKeyAnnotation) {
        return "ALTER TABLE %s ADD CONSTRAINT " + foreignKeyAnnotation.foreignKeyName() + " FOREIGN KEY (" +
                column + " ) REFERENCES " + foreignKeyAnnotation.foreignTableName() + "(" +
                foreignKeyAnnotation.foreignColumnName() + ");";
    }

    private void buildIndexQuery(String column, String indexName, boolean isUnique) {
        if (!indexQueries.containsKey(indexName)) {
            String indexQuery = isUnique ? SQLConstants.CREATE_UNIQUE_INDEX : SQLConstants.CREATE_INDEX + indexName +
                    ") ON %s ( " + column;
            indexQueries.put(indexName, indexQuery);
        } else {
            String indexQuery = indexQueries.get(indexName) + ", " + column;
            indexQueries.put(indexName, indexQuery);
        }
    }

    private boolean canConvertToInteger(Class type) {
        return canConvertToIntValue(type) ||
                canConvertToBooleanValue(type);
    }

    private boolean canConvertToBooleanValue(Class<?> type) {
        return type.isAssignableFrom(Boolean.class) ||
                type.isAssignableFrom(boolean.class);
    }

    private boolean canConvertToIntValue(Class<?> type) {
        return type.isAssignableFrom(int.class) ||
                type.isAssignableFrom(Integer.class) ||
                type.isAssignableFrom(Long.class) ||
                type.isAssignableFrom(long.class);
    }

    private boolean canConvertToReal(Class type) {
        return canConvertToFloat(type) ||
                canConvertToDouble(type);
    }

    private boolean canConvertToFloat(Class<?> type) {
        return type.isAssignableFrom(float.class) ||
                type.isAssignableFrom(Float.class);
    }

    private boolean canConvertToDouble(Class<?> type) {
        return type.isAssignableFrom(double.class) ||
                type.isAssignableFrom(Double.class);
    }

    private boolean canConvertToText(Class<?> type) {
        return type.isAssignableFrom(String.class);
    }

    public String insert(Object o) {
        Field[] publicFields = o.getClass().getFields();
        Field updateField = stream(publicFields)
                .filter(f -> hasPrimaryPositiveValue(f, o))
                .findFirst()
                .orElse(null);

        String tableName = o.getClass().getName();

        if (updateField == null) {
            return createInsertQuery(o, publicFields, tableName);
        } else {
            return createUpdateQuery(o, publicFields, updateField, tableName);
        }
    }

    private String createInsertQuery(Object o, Field[] publicFields, String tableName) {
        StringBuilder insertQuery = new StringBuilder();

        insertQuery.append(SQLConstants.INSERT)
                .append(tableName)
                .append(SQLConstants.OPEN_BRACE);
        String columnsName = stream(publicFields)
                .filter(this::isNotAutoincrementPrimaryKey)
                .map(Field::getName)
                .collect(joining(SQLConstants.COMMA));
        insertQuery.append(columnsName)
                .append(" ) VALUES (");
        String values = stream(publicFields)
                .map(f -> convertToValue(o, f))
                .collect(joining(SQLConstants.COMMA));
        insertQuery.append(values)
                .append(SQLConstants.CLOSE_QUERY);

        return insertQuery.toString();
    }

    private String createUpdateQuery(Object o, Field[] publicFields, Field updateField, String tableName) {
        StringBuilder updateQuery = new StringBuilder();

        updateQuery.append(SQLConstants.UPDATE)
                .append(tableName)
                .append(" SET ");
        List<String> columnsName = stream(publicFields)
                .map(Field::getName)
                .collect(toList());

        List<String> values = stream(publicFields)
                .map(f -> convertToValue(o, f))
                .collect(toList());

        for (int i = 0; i < values.size(); ++i) {
            if (i == 0) {
                updateQuery.append(" ")
                        .append(columnsName.get(i))
                        .append(" = ")
                        .append(values.get(i));
            } else {
                updateQuery.append(", ")
                        .append(columnsName.get(i))
                        .append(" = ")
                        .append(values.get(i));
            }
        }

        updateQuery.append(" ")
                .append("WHERE ")
                .append(convertToColumn(updateField))
                .append(" = ")
                .append(convertToValue(o, updateField))
                .append(SQLConstants.CLOSE_QUERY);

        return updateQuery.toString();
    }

    private boolean hasPrimaryPositiveValue(Field field, Object o) {
        Integer value = null;
        Class<?> type = field.getType();
        if (type.isAssignableFrom(int.class)) {
            try {
                value = field.getInt(o);
            } catch (IllegalAccessException ignored) {
                return false;
            }
        } else if (type.isAssignableFrom(Integer.class)) {
            try {
                value = (Integer) field.get(o);
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }
        KeyAnnotation keyAnnotation = field.getAnnotation(KeyAnnotation.class);
        return keyAnnotation != null && value != null && value > 0;
    }

    private boolean isNotAutoincrementPrimaryKey(Field field) {
        KeyAnnotation keyAnnotation = field.getAnnotation(KeyAnnotation.class);
        return keyAnnotation == null || !keyAnnotation.autoIncrement();
    }

    private String convertToValue(Object o, Field field) {
        Class<?> type = field.getType();
        if (type.isAssignableFrom(boolean.class)) {
            try {
                return String.valueOf(field.getBoolean(o) ? 1 : 0);
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(int.class)) {
            try {
                return String.valueOf(field.getInt(o));
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(long.class)) {
            try {
                return String.valueOf(field.getLong(o));
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(float.class)) {
            try {
                return SQLConstants.SINGLE_QUOTE + String.valueOf(field.getFloat(o)).replace('.', ',') + SQLConstants.SINGLE_QUOTE;
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(double.class)) {
            try {
                return SQLConstants.SINGLE_QUOTE + String.valueOf(field.getDouble(o)).replace('.', ',') + SQLConstants.SINGLE_QUOTE;
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(Boolean.class)) {
            try {
                Boolean value = (Boolean) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return String.valueOf(value ? 1 : 0);
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(Integer.class)) {
            try {
                Integer value = (Integer) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return String.valueOf(value);
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(Long.class)) {
            try {
                Long value = (Long) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return String.valueOf(value);
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(Float.class)) {
            try {
                Float value = (Float) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return SQLConstants.SINGLE_QUOTE + String.valueOf(value).replace('.', ',') + SQLConstants.SINGLE_QUOTE;
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(Double.class)) {
            try {
                Double value = (Double) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return SQLConstants.SINGLE_QUOTE + String.valueOf(value).replace('.', ',') + SQLConstants.SINGLE_QUOTE;
            } catch (IllegalAccessException ignored) {

            }
        } else if (type.isAssignableFrom(String.class)) {
            try {
                String value = (String) field.get(o);
                if (value == null) {
                    return SQLConstants.NULL;
                }
                return SQLConstants.SINGLE_QUOTE + value + SQLConstants.SINGLE_QUOTE;
            } catch (IllegalAccessException ignored) {

            }
        }
        return null;
    }

    private static class SQLConstants {
        private static final String CREATE_UNIQUE_INDEX = "CREATE UNIQUE INDEX (";
        private static final String CREATE_INDEX = "CREATE INDEX (";
        private static final String INSERT = "INSERT INTO ";
        private static final String UPDATE = "UPDATE ";
        private static final String COMMA = ",";
        private static final String CLOSE_QUERY = ");";
        private static final String OPEN_BRACE = " (";
        private static final String PRIMARY_KEY = "INTEGER NOT NULL PRIMARY KEY";
        private static final String AUTOINCREMENT_PRIMARY_KEY = "INTEGER PRIMARY KEY AUTOINCREMENT";
        private static final String INTEGER = "INTEGER";
        private static final String TEXT = "TEXT";
        private static final String REAL = "REAL";
        private static final String NULL = "null";
        private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS ";
        private static final String SINGLE_QUOTE = "\'";
    }
}
