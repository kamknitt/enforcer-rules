package com.github.kamknitt.enforcer.rules.packages.cycles.model;

import java.util.ArrayList;

public class Cycle extends ArrayList<String> {
    public Cycle(int size) {
        super(size);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cycle)) return false;

        Cycle c = (Cycle) o;

        if(size() != c.size()) return false;
        for(int i=0; i<size(); i++) {
            if(!get(i).equals(c.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        return result;
    }
}
