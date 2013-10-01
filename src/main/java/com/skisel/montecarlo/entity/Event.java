package com.skisel.montecarlo.entity;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 30.09.13
 * Time: 22:26
 * To change this template use File | Settings | File Templates.
 */
public class Event {
    private Integer eventId;
    private Integer key;
    private List<Loss> losses;

    public Event() {
    }

    public Event(Integer eventId, Integer key, List<Loss> losses) {
        this.eventId = eventId;
        this.key = key;
        this.losses = losses;
    }

    public Integer getEventId() {
        return eventId;
    }

    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }

    public Integer getKey() {
        return key;
    }

    public void setKey(Integer key) {
        this.key = key;
    }

    public List<Loss> getLosses() {
        return losses;
    }

    public void setLosses(List<Loss> losses) {
        this.losses = losses;
    }
}
