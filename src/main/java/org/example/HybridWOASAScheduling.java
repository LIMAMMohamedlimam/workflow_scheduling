package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class HybridWOASAScheduling implements SchedulingAlgorithm {
    
    // WOA Parameters
    private static final int POPULATION_SIZE = 30;
    private static final int MAX_ITERATIONS = 100;
    
    // SA Parameters
    private static final boolean ENABLE_SA = true;
    private static final double SA_INITIAL_TEMP = 100.0;
    private static final double SA_MIN_TEMP = 0.1;
    private static final double SA_COOLING_RATE = 0.95;
    private static final int SA_STEPS_PER_TEMP = 50;
    
    // Multi-objective weights
    private final double wMakespan;
    private final double wEnergy;
    private final double wCost;
    
    private Random rand = new Random();
    
    // Archive for Pareto-optimal solutions
    private List<ParetoEntry> paretoArchive = new ArrayList<>();
    
    /**
     * Pareto solution entry
     */
    private static class ParetoEntry {
        final int[] schedule;
        final double makespan;
        final double energy;
        final double cost;
        double crowdingDistance;
        
        ParetoEntry(int[] schedule, double makespan, double energy, double cost) {
            this.schedule = schedule.clone();
            this.makespan = makespan;
            this.energy = energy;
            this.cost = cost;
            this.crowdingDistance = 0.0;
        }
    }
    
    public HybridWOASAScheduling(double makespanWeight, double energyWeight, double costWeight) {
        double sum = makespanWeight + energyWeight + costWeight;
        this.wMakespan = makespanWeight / sum;
        this.wEnergy = energyWeight / sum;
        this.wCost = costWeight / sum;
    }
    
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms,
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        System.out.printf("Starting Hybrid WOA-SA (Weights: Makespan=%.2f, Energy=%.2f, Cost=%.2f)\n",
                         wMakespan, wEnergy, wCost);
        
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        // Initialize whale population
        double[][] whales = new double[POPULATION_SIZE][numTasks];
        double[] fitness = new double[POPULATION_SIZE];
        double[][] objectives = new double[POPULATION_SIZE][3]; // [makespan, energy, cost]
        
        // Initialize population randomly
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int t = 0; t < numTasks; t++) {
                whales[i][t] = rand.nextInt(numVms);
            }
        }
        
        // Evaluate initial population
        double[] maxObjectives = new double[3]; // For normalization
        for (int i = 0; i < POPULATION_SIZE; i++) {
            int[] schedule = discretizeSchedule(whales[i], numVms);
            objectives[i][0] = calculateMakespan(schedule, cloudlets, vms, tasks);
            objectives[i][1] = calculateEnergy(schedule, cloudlets, vms, tasks, nodes);
            objectives[i][2] = calculateCost(schedule, cloudlets, vms, tasks, nodes);
            
            // Track max for normalization
            for (int j = 0; j < 3; j++) {
                maxObjectives[j] = Math.max(maxObjectives[j], objectives[i][j]);
            }
            
            fitness[i] = scalarFitness(objectives[i], maxObjectives);
            addToParetoArchive(schedule, objectives[i][0], objectives[i][1], objectives[i][2]);
        }
        
        // Select initial leader
        double[] bestWhale = selectLeader(whales, objectives, maxObjectives, numVms);
        int[] bestSchedule = discretizeSchedule(bestWhale, numVms);
        double bestFitness = fitness[0];
        
        System.out.printf("  Initial archive size: %d Pareto solutions\n", paretoArchive.size());
        
        // WOA Main Loop
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS); // Decreases from 2 to 0
            
            for (int i = 0; i < POPULATION_SIZE; i++) {
                // WOA position update
                for (int t = 0; t < numTasks; t++) {
                    double r1 = rand.nextDouble();
                    double r2 = rand.nextDouble();
                    double A = 2.0 * a * r1 - a;
                    double C = 2.0 * r2;
                    double p = rand.nextDouble();
                    
                    double newPos;
                    if (p < 0.5) {
                        if (Math.abs(A) < 1.0) {
                            // Encircling prey (exploitation)
                            double D = Math.abs(C * bestWhale[t] - whales[i][t]);
                            newPos = bestWhale[t] - A * D;
                        } else {
                            // Search for prey (exploration)
                            int randIdx = rand.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randIdx][t] - whales[i][t]);
                            newPos = whales[randIdx][t] - A * D;
                        }
                    } else {
                        // Spiral bubble-net attacking
                        double b = 1.0;
                        double l = rand.nextDouble() * 2.0 - 1.0;
                        double D = Math.abs(bestWhale[t] - whales[i][t]);
                        newPos = D * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + bestWhale[t];
                    }
                    
                    whales[i][t] = discretizeVm(newPos, numVms);
                }
                
                // Evaluate new position
                int[] schedule = discretizeSchedule(whales[i], numVms);
                objectives[i][0] = calculateMakespan(schedule, cloudlets, vms, tasks);
                objectives[i][1] = calculateEnergy(schedule, cloudlets, vms, tasks, nodes);
                objectives[i][2] = calculateCost(schedule, cloudlets, vms, tasks, nodes);
                
                // Update max objectives
                for (int j = 0; j < 3; j++) {
                    maxObjectives[j] = Math.max(maxObjectives[j], objectives[i][j]);
                }
                
                fitness[i] = scalarFitness(objectives[i], maxObjectives);
                addToParetoArchive(schedule, objectives[i][0], objectives[i][1], objectives[i][2]);
            }
            
            // Select new leader
            bestWhale = selectLeader(whales, objectives, maxObjectives, numVms);
            bestSchedule = discretizeSchedule(bestWhale, numVms);
            
            if ((iter + 1) % 20 == 0) {
                System.out.printf("  Iteration %d: Archive size = %d\n", iter + 1, paretoArchive.size());
            }
        }
        
        // Apply SA local search on leader
        if (ENABLE_SA) {
            System.out.println("  Applying SA local search on leader...");
            bestSchedule = improveWithSA(bestSchedule, cloudlets, vms, tasks, nodes, maxObjectives);
        }
        
        // Get final best from archive
        computeCrowdingDistance();
        paretoArchive.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
        ParetoEntry finalBest = paretoArchive.get(0);
        
        System.out.println("✓ Hybrid WOA-SA completed");
        System.out.printf("  Final archive: %d Pareto-optimal solutions\n", paretoArchive.size());
        System.out.printf("  Selected solution: Makespan=%.4f, Energy=%.4f, Cost=%.4f\n",
                         finalBest.makespan, finalBest.energy, finalBest.cost);
        
        // Apply best solution
        return applySolution(finalBest.schedule, cloudlets, vms);
    }
    
    /**
     * Discretize continuous whale position to VM indices
     */
    private int[] discretizeSchedule(double[] whale, int numVms) {
        int[] schedule = new int[whale.length];
        for (int i = 0; i < whale.length; i++) {
            schedule[i] = (int) Math.round(whale[i]) % numVms;
            if (schedule[i] < 0) schedule[i] += numVms;
        }
        return schedule;
    }
    
    private double discretizeVm(double x, int numVms) {
        long v = Math.round(x);
        if (v < 0) v = -v;
        return (double) (v % numVms);
    }
    
    /**
     * Calculate scalar fitness from normalized objectives
     */
    private double scalarFitness(double[] objectives, double[] maxObjectives) {
        double normMakespan = objectives[0] / Math.max(1e-9, maxObjectives[0]);
        double normEnergy = objectives[1] / Math.max(1e-9, maxObjectives[1]);
        double normCost = objectives[2] / Math.max(1e-9, maxObjectives[2]);
        
        return wMakespan * normMakespan + wEnergy * normEnergy + wCost * normCost;
    }
    
    /**
     * Select leader using crowding distance from Pareto archive
     */
    private double[] selectLeader(double[][] whales, double[][] objectives, 
                                  double[] maxObjectives, int numVms) {
        if (paretoArchive.isEmpty()) {
            // Fallback: return best by scalar fitness
            int bestIdx = 0;
            double bestFit = scalarFitness(objectives[0], maxObjectives);
            for (int i = 1; i < whales.length; i++) {
                double fit = scalarFitness(objectives[i], maxObjectives);
                if (fit < bestFit) {
                    bestFit = fit;
                    bestIdx = i;
                }
            }
            return whales[bestIdx].clone();
        }
        
        // Select from archive using crowding distance
        computeCrowdingDistance();
        paretoArchive.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
        
        // Select from top solutions
        int k = Math.min(5, paretoArchive.size());
        ParetoEntry selected = paretoArchive.get(rand.nextInt(k));
        
        // Convert back to continuous representation
        double[] leader = new double[selected.schedule.length];
        for (int i = 0; i < leader.length; i++) {
            leader[i] = selected.schedule[i];
        }
        return leader;
    }
    
    /**
     * Add solution to Pareto archive (remove dominated solutions)
     */
    private void addToParetoArchive(int[] schedule, double makespan, double energy, double cost) {
        // Check if new solution is dominated
        for (ParetoEntry entry : paretoArchive) {
            if (dominates(entry.makespan, entry.energy, entry.cost, makespan, energy, cost)) {
                return; // New solution is dominated, reject
            }
        }
        
        // Remove solutions dominated by new solution
        paretoArchive.removeIf(entry -> 
            dominates(makespan, energy, cost, entry.makespan, entry.energy, entry.cost));
        
        // Add new solution
        paretoArchive.add(new ParetoEntry(schedule, makespan, energy, cost));
    }
    
    /**
     * Check if solution 1 dominates solution 2
     */
    private boolean dominates(double m1, double e1, double c1, double m2, double e2, double c2) {
        boolean noWorse = (m1 <= m2) && (e1 <= e2) && (c1 <= c2);
        boolean better = (m1 < m2) || (e1 < e2) || (c1 < c2);
        return noWorse && better;
    }
    
    /**
     * Compute crowding distance for archive solutions
     */
    private void computeCrowdingDistance() {
        for (ParetoEntry e : paretoArchive) {
            e.crowdingDistance = 0.0;
        }
        
        if (paretoArchive.size() <= 2) {
            for (ParetoEntry e : paretoArchive) {
                e.crowdingDistance = Double.POSITIVE_INFINITY;
            }
            return;
        }
        
        // Crowding on each objective
        crowdingOnObjective("makespan");
        crowdingOnObjective("energy");
        crowdingOnObjective("cost");
    }
    
    private void crowdingOnObjective(String objective) {
        paretoArchive.sort(Comparator.comparingDouble(a -> getObjectiveValue(a, objective)));
        
        double min = getObjectiveValue(paretoArchive.get(0), objective);
        double max = getObjectiveValue(paretoArchive.get(paretoArchive.size() - 1), objective);
        
        paretoArchive.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
        paretoArchive.get(paretoArchive.size() - 1).crowdingDistance = Double.POSITIVE_INFINITY;
        
        double range = (max - min);
        if (range == 0) range = 1.0;
        
        for (int i = 1; i < paretoArchive.size() - 1; i++) {
            if (Double.isInfinite(paretoArchive.get(i).crowdingDistance)) continue;
            
            double prev = getObjectiveValue(paretoArchive.get(i - 1), objective);
            double next = getObjectiveValue(paretoArchive.get(i + 1), objective);
            paretoArchive.get(i).crowdingDistance += (next - prev) / range;
        }
    }
    
    private double getObjectiveValue(ParetoEntry entry, String objective) {
        switch (objective) {
            case "makespan": return entry.makespan;
            case "energy": return entry.energy;
            case "cost": return entry.cost;
            default: return 0;
        }
    }
    
    /**
     * SA local search to improve solution
     */
    private int[] improveWithSA(int[] initial, List<Cloudlet> cloudlets, List<Vm> vms,
                                List<TaskSpec> tasks, List<NodeSpec> nodes, double[] maxObjectives) {
        int[] current = initial.clone();
        double[] currentObj = evaluateObjectives(current, cloudlets, vms, tasks, nodes);
        double currentFit = scalarFitness(currentObj, maxObjectives);
        
        int[] best = current.clone();
        double[] bestObj = currentObj.clone();
        double bestFit = currentFit;
        
        double temperature = SA_INITIAL_TEMP;
        
        while (temperature > SA_MIN_TEMP) {
            for (int step = 0; step < SA_STEPS_PER_TEMP; step++) {
                // Generate neighbor
                int[] neighbor = generateNeighbor(current, vms.size());
                double[] neighborObj = evaluateObjectives(neighbor, cloudlets, vms, tasks, nodes);
                double neighborFit = scalarFitness(neighborObj, maxObjectives);
                
                // Accept or reject
                double delta = neighborFit - currentFit;
                if (delta <= 0 || rand.nextDouble() < Math.exp(-delta / temperature)) {
                    current = neighbor;
                    currentObj = neighborObj;
                    currentFit = neighborFit;
                    
                    addToParetoArchive(current, currentObj[0], currentObj[1], currentObj[2]);
                }
                
                // Update best
                if (currentFit < bestFit) {
                    best = current.clone();
                    bestObj = currentObj.clone();
                    bestFit = currentFit;
                }
            }
            
            temperature *= SA_COOLING_RATE;
        }
        
        return best;
    }
    
    private int[] generateNeighbor(int[] schedule, int numVms) {
        int[] neighbor = schedule.clone();
        int task = rand.nextInt(schedule.length);
        int newVm = rand.nextInt(numVms);
        neighbor[task] = newVm;
        return neighbor;
    }
    
    private double[] evaluateObjectives(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms,
                                       List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double[] objectives = new double[3];
        objectives[0] = calculateMakespan(schedule, cloudlets, vms, tasks);
        objectives[1] = calculateEnergy(schedule, cloudlets, vms, tasks, nodes);
        objectives[2] = calculateCost(schedule, cloudlets, vms, tasks, nodes);
        return objectives;
    }
    
    private double calculateMakespan(int[] schedule, List<Cloudlet> cloudlets, 
                                    List<Vm> vms, List<TaskSpec> tasks) {
        double[] vmFinishTime = new double[vms.size()];
        
        for (int i = 0; i < schedule.length; i++) {
            int vmIndex = schedule[i];
            double execTime = (double) tasks.get(i).instructions / vms.get(vmIndex).getMips();
            vmFinishTime[vmIndex] += execTime;
        }
        
        double makespan = 0;
        for (double time : vmFinishTime) {
            if (time > makespan) makespan = time;
        }
        return makespan;
    }
    
    private double calculateEnergy(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms,
                                  List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double[] vmActiveTime = new double[vms.size()];
        
        for (int i = 0; i < schedule.length; i++) {
            int vmIndex = schedule[i];
            double execTime = (double) tasks.get(i).instructions / vms.get(vmIndex).getMips();
            vmActiveTime[vmIndex] += execTime;
        }
        
        double totalEnergy = 0.0;
        for (int j = 0; j < vms.size(); j++) {
            if (vmActiveTime[j] > 0) {
                NodeSpec node = nodes.get(j);
                double avgPower = (node.idlePower + node.activePower) / 2.0;
                totalEnergy += avgPower * vmActiveTime[j];
            }
        }
        
        return totalEnergy;
    }
    
    private double calculateCost(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms,
                                List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double totalCost = 0.0;
        
        for (int i = 0; i < schedule.length; i++) {
            int vmIndex = schedule[i];
            TaskSpec task = tasks.get(i);
            NodeSpec node = nodes.get(vmIndex);
            Vm vm = vms.get(vmIndex);
            
            double execTime = (double) task.instructions / vm.getMips();
            double computeCost = execTime * node.costPerCpuSecond;
            double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
            double bwCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
            
            totalCost += computeCost + memoryCost + bwCost;
        }
        
        return totalCost;
    }
    
    private Map<Integer, Integer> applySolution(int[] schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = vms.get(schedule[i]);
            cloudlet.setVm(assignedVm);
            mapping.put((int) cloudlet.getId(), (int) assignedVm.getId());
        }
        
        return mapping;
    }
}
