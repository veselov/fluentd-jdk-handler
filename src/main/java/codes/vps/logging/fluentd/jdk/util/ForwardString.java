package codes.vps.logging.fluentd.jdk.util;

import java.util.NoSuchElementException;

public class ForwardString extends StringWinder {

    private int index;
    private int limit;
    private boolean expired;

    public ForwardString(CharSequence src) {

        super(src);
        int limit = src.length();
        index = 0;
        expired = limit <= 0;
        this.limit = limit;

    }

    @Override
    public boolean hasNext() {
        return !expired;
    }

    @Override
    public char next() {

        char c = peek();

        if (++index == limit) { expired = true; }

        return c;

    }

    @Override
    public char peek() {
        if (expired) {
            throw new NoSuchElementException("ran out");
        }
        return array[this.index];
    }

    @Override
    public String remainder() {
        return String.valueOf(array, this.index, this.limit - this.index);
    }

    @Override
    public int getLastIndex() {
        return index - 1;
    }

}
