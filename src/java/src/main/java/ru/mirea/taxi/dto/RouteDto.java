package ru.mirea.taxi.dto;

import java.util.List;

public class RouteDto {
    private Integer distanceMeters;
    private Integer durationSeconds;
    private List<List<Double>> coordinates; // [[lat, lon], [lat, lon], ...]
    private String provider;

    private List<Leg> legs;
    private List<List<Double>> waypoints;

    public record Leg(double distanceKm, double durationMinutes) {}

    public List<Leg> getLegs() {
        return legs;
    }

    public void setLegs(List<Leg> legs) {
        this.legs = legs;
    }

    public List<List<Double>> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<List<Double>> waypoints) {
        this.waypoints = waypoints;
    }

    public double getDistanceKm() {
        return distanceMeters / 1000.0;
    }

    public double getDurationMinutes() {
        return durationSeconds / 60.0;
    }

    public RouteDto() {}

    public RouteDto(
            Integer distanceMeters,
            Integer durationSeconds,
            List<List<Double>> coordinates,
            String provider) {
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.coordinates = coordinates;
        this.provider = provider;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Integer distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<List<Double>> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<List<Double>> coordinates) {
        this.coordinates = coordinates;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
