package com.intuit.karate.runtime;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author pthomas3
 */
@XmlRootElement
public class Cat {
    
    private int id;
    private String name;
    private List<Cat> kittens;
    
    public void addKitten(Cat kitten) {
        if (kittens == null) {
            kittens = new ArrayList<>();
        }
        kittens.add(kitten);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }        

    public List<Cat> getKittens() {
        return kittens;
    }

    public void setKittens(List<Cat> kittens) {
        this.kittens = kittens;
    }        
    
}