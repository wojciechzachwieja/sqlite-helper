class TestObjects {

    static class Test1 {
        public Integer intfield;
        public int intfield2;
        public Float aFloat;
        public float aFloat2;
        public Double aDouble;
        public double aDouble2;
        public Long aLong;
        public long aLong2;
        public String string;
        public Boolean aBoolean;
        public boolean aBoolean2;
        private boolean aBoolean3;
        protected String scd;
        String sdcsdc;
    }


    static class Test2 {
        @KeyAnnotation(autoIncrement = true)
        public Integer intfield;
        @ForeignKeyAnnotation(foreignKeyName = "fk", foreignTableName = "TestObjects$Test1", foreignColumnName = "intfield2")
        public int intfield2;
        @IndexAnnotation(indexName = "afloat", isUnique = true)
        public Float aFloat;
        public float aFloat2;
        public Double aDouble;
        public double aDouble2;
        public Long aLong;
        public long aLong2;
        public String string;
        public Boolean aBoolean;
        public boolean aBoolean2;
        private boolean aBoolean3;
        protected String scd;
        String sdcsdc;
    }
}
