package org.example;
import org.cloudsimplus.datacenters.Datacenter;

public class NodeSpec {
    String nodeId;
    double mipsRating;
    double costPerCpuSecond;
    double costPerMem;
    double costPerBw;
    double costPerStorage;
    Datacenter datacenter;
    double idlePower;
    double activePower;

    public NodeSpec(String nodeId, double mips, double cpuCost, double memCost, double bwCost,
                    double idlePower, double activePower) {
        this.nodeId = nodeId;
        this.mipsRating = mips;
        this.costPerCpuSecond = cpuCost;
        this.costPerMem = memCost;
        this.costPerBw = bwCost;
        this.costPerStorage = 0.001;
        this.idlePower = idlePower;
        this.activePower = activePower;
    }
}