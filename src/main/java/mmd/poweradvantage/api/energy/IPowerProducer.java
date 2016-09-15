package mmd.poweradvantage.api.energy;

public interface IPowerProducer {

    long takePower(long power, boolean simulated);
}