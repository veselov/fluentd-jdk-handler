package codes.vps.logging.fluentd.jdk.util;

public abstract class StringWinder {

    protected char [] array;

    public StringWinder(CharSequence src) {

        int _l = src.length();
        char [] array = new char[_l];
        for (int i=0; i<_l; i++) {
            array[i] = src.charAt(i);
        }

        this.array = array;

    }

    public abstract boolean hasNext();

    public abstract char next();

    public abstract char peek();

    public abstract String remainder();

    /**
     * Returns index that was used to retrieve the character using
     * {@link #next()} method.
     * @return index of the previously returned character.
     */
    public abstract int getLastIndex();

}
