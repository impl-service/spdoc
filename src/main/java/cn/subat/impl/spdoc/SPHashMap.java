package cn.subat.impl.spdoc;

import java.util.HashMap;

public class SPHashMap extends HashMap<Object,Object> {

    public SPHashMap(Object... p) {
        for (int i = 0; i < p.length; i = i +2) {
            this.put(p[i],p[i+1]);
        }
    }
}
