error id: file://<WORKSPACE>/src/main/java/org/example/WOARDAScheduling.java:SchedulingAlgorithm#
file://<WORKSPACE>/src/main/java/org/example/WOARDAScheduling.java
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 179
uri: file://<WORKSPACE>/src/main/java/org/example/WOARDAScheduling.java
text:
```scala
package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class WOARDAScheduling implements SchedulingAlgorithm@@ {
    
    // Algorithm Parameters
    private static final int POPULATION_SIZE = 40;
    private static final int MAX_ITERATIONS = 100;
    private static final double ELITE_RATE = 0.2; // Top 20% are "male deers"
    private static final double HAREM_RATE = 0.5; // Harems for breeding
    
    // Multi-objective weights
    private final double wMakespan;
    private final double wEnergy;
    private final double wCost;
    
    private Random rand = new Random();
    
    // Pareto archive
    private List<ParetoSolution> paretoArchive = new ArrayList<>();
    
    /**
     * Pareto solution with objectives
     */
    private static class ParetoSolution {
        final int[] schedule;
        final double makespan;
        final double energy;
        final double cost;
        double crowdingDistance;
        double fitness;
        
        ParetoSolution(int[] schedule, double makespan, double energy, double cost) {
            this.schedule = schedule.clone();
            this.makespan = makespan;
            this.energy = energy;
            this.cost = cost;
            this.crowdingDistance = 0.0;
            this.fitness = 0.0;
        }
    }
    
    /**
     * Deer individual in population
     */
    private static class Deer {
        int[] schedule;
        double makespan;
        double energy;
        double cost;
        double fitness;
        
        Deer(int[] schedule) {
            this.schedule = schedule.clone();
        }
    }
    
    public WOARDAScheduling(double makespanWeight, double energyWeight, double costWeight) {
        double sum = makespanWeight + energyWeight + costWeight;
        this.wMakespan = makespanWeight / sum;
        this.wEnergy = energyWeight / sum;
        this.wCost = costWeight / sum;
    }
    
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms,
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        System.out.printf("Starting Hybrid WOA-RDA (Weights: Makespan=%.2f, Energy=%.2f, Cost=%.2f)\n",
                         wMakespan, wEnergy, wCost);
        
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        // Initialize deer population (herd)
        Deer[] population = new Deer[POPULATION_SIZE];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            int[] schedule = new int[numTasks];
            for (int j = 0; j < numTasks; j++) {
                schedule[j] = rand.nextInt(numVms);
            }
            population[i] = new Deer(schedule);
        }
        
        // Evaluate initial population
        double[] maxObjectives = new double[3];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            evaluateDeer(population[i], cloudlets, vms, tasks, nodes);
            
            // Track max for normalization
            maxObjectives[0] = Math.max(maxObjectives[0], population[i].makespan);
            maxObjectives[1] = Math.max(maxObjectives[1], population[i].energy);
            maxObjectives[2] = Math.max(maxObjectives[2], population[i].cost);
            
            // Add to Pareto archive
            addToParetoArchive(population[i].schedule, population[i].makespan, 
                             population[i].energy, population[i].cost);
        }
        
        // Calculate scalar fitness
        for (int i = 0; i < POPULATION_SIZE; i++) {
            population[i].fitness = scalarFitness(population[i], maxObjectives);
        }
        
        // Find initial best (alpha deer/leader whale)
        Deer bestDeer = findBestDeer(population);
        
        System.out.printf("  Initial: Makespan=%.2f, Energy=%.2f, Cost=%.2f, Archive=%d\n",
                         bestDeer.makespan, bestDeer.energy, bestDeer.cost, paretoArchive.size());
        
        // Main Hybrid Loop
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            
            // ========== PHASE 1: WOA (Exploration) ==========
            double a = 2.0 * (1.0 - (double)iter / MAX_ITERATIONS); // Decreases from 2 to 0
            
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = rand.nextDouble();
                double A = 2.0 * a * r - a;
                double C = 2.0 * rand.nextDouble();
                double p = rand.nextDouble();
                
                int[] newSchedule;
                
                if (p < 0.5) {
                    if (Math.abs(A) < 1.0) {
                        // Encircling prey (exploitation around best)
                        newSchedule = encirclingPrey(population[i].schedule, bestDeer.schedule, 
                                                    A, C, numVms);
                    } else {
                        // Search for prey (exploration)
                        int randIdx = rand.nextInt(POPULATION_SIZE);
                        newSchedule = encirclingPrey(population[i].schedule, 
                                                    population[randIdx].schedule, A, C, numVms);
                    }
                } else {
                    // Spiral bubble-net attacking
                    newSchedule = spiralAttack(population[i].schedule, bestDeer.schedule, numVms);
                }
                
                // Evaluate new position
                Deer newDeer = new Deer(newSchedule);
                evaluateDeer(newDeer, cloudlets, vms, tasks, nodes);
                
                // Update max objectives
                maxObjectives[0] = Math.max(maxObjectives[0], newDeer.makespan);
                maxObjectives[1] = Math.max(maxObjectives[1], newDeer.energy);
                maxObjectives[2] = Math.max(maxObjectives[2], newDeer.cost);
                
                newDeer.fitness = scalarFitness(newDeer, maxObjectives);
                
                // Accept if better
                if (newDeer.fitness < population[i].fitness) {
                    population[i] = newDeer;
                    addToParetoArchive(newDeer.schedule, newDeer.makespan, 
                                     newDeer.energy, newDeer.cost);
                }
            }
            
            // ========== PHASE 2: RDA (Exploitation) ==========
            applyRedDeerAlgorithm(population, numVms, cloudlets, vms, tasks, nodes, maxObjectives);
            
            // Update best deer
            bestDeer = findBestDeer(population);
            
            if ((iter + 1) % 20 == 0) {
                System.out.printf("  Iteration %d: Best fitness=%.4f, Archive=%d\n",
                                iter + 1, bestDeer.fitness, paretoArchive.size());
            }
        }
        
        // Select final solution from Pareto archive
        computeCrowdingDistance();
        paretoArchive.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
        
        ParetoSolution finalSolution = paretoArchive.get(0);
        
        System.out.println("✓ Hybrid WOA-RDA completed");
        System.out.printf("  Final archive: %d Pareto-optimal solutions\n", paretoArchive.size());
        System.out.printf("  Selected: Makespan=%.4f, Energy=%.4f, Cost=%.4f\n",
                         finalSolution.makespan, finalSolution.energy, finalSolution.cost);
        
        // Apply solution
        return applySolution(finalSolution.schedule, cloudlets, vms);
    }
    
    /**
     * WOA: Encircling prey mechanism
     */
    private int[] encirclingPrey(int[] current, int[] target, double A, double C, int numVms) {
        int[] newSchedule = new int[current.length];
        
        for (int i = 0; i < current.length; i++) {
            // D = |C * X_target - X_current|
            double D = Math.abs(C * target[i] - current[i]);
            // X_new = X_target - A * D
            double newPos = target[i] - A * D;
            
            newSchedule[i] = discretizeVm(newPos, numVms);
        }
        
        return newSchedule;
    }
    
    /**
     * WOA: Spiral bubble-net attacking
     */
    private int[] spiralAttack(int[] current, int[] best, int numVms) {
        int[] newSchedule = new int[current.length];
        double b = 1.0;
        double l = rand.nextDouble() * 2.0 - 1.0; // [-1, 1]
        
        for (int i = 0; i < current.length; i++) {
            double distance = Math.abs(best[i] - current[i]);
            double newPos = distance * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + best[i];
            
            newSchedule[i] = discretizeVm(newPos, numVms);
        }
        
        return newSchedule;
    }
    
    /**
     * RDA: Red Deer Algorithm phase
     */
    private void applyRedDeerAlgorithm(Deer[] population, int numVms, 
                                      List<Cloudlet> cloudlets, List<Vm> vms,
                                      List<TaskSpec> tasks, List<NodeSpec> nodes,
                                      double[] maxObjectives) {
        // Sort population by fitness (best to worst)
        Arrays.sort(population, Comparator.comparingDouble(d -> d.fitness));
        
        int numMales = (int)(ELITE_RATE * POPULATION_SIZE);
        int numHinds = POPULATION_SIZE - numMales;
        int numHarems = Math.max(1, numMales / 2);
        
        // Male deer compete and roar
        for (int i = 0; i < numMales; i++) {
            // Roaring: local mutation
            if (rand.nextDouble() < 0.3) {
                int[] mutated = roaring(population[i].schedule, numVms);
                Deer newDeer = new Deer(mutated);
                evaluateDeer(newDeer, cloudlets, vms, tasks, nodes);
                newDeer.fitness = scalarFitness(newDeer, maxObjectives);
                
                if (newDeer.fitness < population[i].fitness) {
                    population[i] = newDeer;
                    addToParetoArchive(newDeer.schedule, newDeer.makespan, 
                                     newDeer.energy, newDeer.cost);
                }
            }
            
            // Fighting: crossover between two males
            if (i + 1 < numMales && rand.nextDouble() < 0.4) {
                int[] offspring = fighting(population[i].schedule, population[i + 1].schedule);
                Deer newDeer = new Deer(offspring);
                evaluateDeer(newDeer, cloudlets, vms, tasks, nodes);
                newDeer.fitness = scalarFitness(newDeer, maxObjectives);
                
                // Replace worse male if offspring is better
                int worseIdx = population[i].fitness > population[i + 1].fitness ? i : i + 1;
                if (newDeer.fitness < population[worseIdx].fitness) {
                    population[worseIdx] = newDeer;
                    addToParetoArchive(newDeer.schedule, newDeer.makespan, 
                                     newDeer.energy, newDeer.cost);
                }
            }
        }
        
        // Mating: males breed with hinds in harems
        for (int h = 0; h < numHarems; h++) {
            int maleIdx = h % numMales;
            int hindStart = numMales + (h * numHinds / numHarems);
            int hindEnd = numMales + ((h + 1) * numHinds / numHarems);
            
            for (int i = hindStart; i < Math.min(hindEnd, POPULATION_SIZE); i++) {
                if (rand.nextDouble() < 0.5) {
                    int[] offspring = mating(population[maleIdx].schedule, population[i].schedule, 
                                           numVms);
                    Deer newDeer = new Deer(offspring);
                    evaluateDeer(newDeer, cloudlets, vms, tasks, nodes);
                    newDeer.fitness = scalarFitness(newDeer, maxObjectives);
                    
                    if (newDeer.fitness < population[i].fitness) {
                        population[i] = newDeer;
                        addToParetoArchive(newDeer.schedule, newDeer.makespan, 
                                         newDeer.energy, newDeer.cost);
                    }
                }
            }
        }
    }
    
    /**
     * RDA: Roaring - local mutation
     */
    private int[] roaring(int[] schedule, int numVms) {
        int[] mutated = schedule.clone();
        int pos = rand.nextInt(schedule.length);
        mutated[pos] = rand.nextInt(numVms);
        return mutated;
    }
    
    /**
     * RDA: Fighting - crossover between two males
     */
    private int[] fighting(int[] male1, int[] male2) {
        int[] offspring = new int[male1.length];
        int crossPoint = rand.nextInt(male1.length);
        
        for (int i = 0; i < male1.length; i++) {
            offspring[i] = (i < crossPoint) ? male1[i] : male2[i];
        }
        
        return offspring;
    }
    
    /**
     * RDA: Mating - male breeds with hind
     */
    private int[] mating(int[] male, int[] hind, int numVms) {
        int[] offspring = new int[male.length];
        
        for (int i = 0; i < male.length; i++) {
            if (rand.nextDouble() < 0.5) {
                offspring[i] = male[i];
            } else {
                offspring[i] = hind[i];
            }
            
            // Small mutation chance
            if (rand.nextDouble() < 0.1) {
                offspring[i] = rand.nextInt(numVms);
            }
        }
        
        return offspring;
    }
    
    private int discretizeVm(double pos, int numVms) {
        int vm = (int)Math.round(pos) % numVms;
        if (vm < 0) vm += numVms;
        return vm;
    }
    
    private void evaluateDeer(Deer deer, List<Cloudlet> cloudlets, List<Vm> vms,
                             List<TaskSpec> tasks, List<NodeSpec> nodes) {
        deer.makespan = calculateMakespan(deer.schedule, cloudlets, vms, tasks);
        deer.energy = calculateEnergy(deer.schedule, cloudlets, vms, tasks, nodes);
        deer.cost = calculateCost(deer.schedule, cloudlets, vms, tasks, nodes);
    }
    
    private double scalarFitness(Deer deer, double[] maxObjectives) {
        double normMakespan = deer.makespan / Math.max(1e-9, maxObjectives[0]);
        double normEnergy = deer.energy / Math.max(1e-9, maxObjectives[1]);
        double normCost = deer.cost / Math.max(1e-9, maxObjectives[2]);
        
        return wMakespan * normMakespan + wEnergy * normEnergy + wCost * normCost;
    }
    
    private Deer findBestDeer(Deer[] population) {
        Deer best = population[0];
        for (int i = 1; i < population.length; i++) {
            if (population[i].fitness < best.fitness) {
                best = population[i];
            }
        }
        return best;
    }
    
    private void addToParetoArchive(int[] schedule, double makespan, double energy, double cost) {
        // Check if dominated
        for (ParetoSolution sol : paretoArchive) {
            if (dominates(sol.makespan, sol.energy, sol.cost, makespan, energy, cost)) {
                return; // Dominated, reject
            }
        }
        
        // Remove dominated solutions
        paretoArchive.removeIf(sol -> 
            dominates(makespan, energy, cost, sol.makespan, sol.energy, sol.cost));
        
        // Add new solution
        paretoArchive.add(new ParetoSolution(schedule, makespan, energy, cost));
    }
    
    private boolean dominates(double m1, double e1, double c1, double m2, double e2, double c2) {
        boolean noWorse = (m1 <= m2) && (e1 <= e2) && (c1 <= c2);
        boolean better = (m1 < m2) || (e1 < e2) || (c1 < c2);
        return noWorse && better;
    }
    
    private void computeCrowdingDistance() {
        for (ParetoSolution sol : paretoArchive) {
            sol.crowdingDistance = 0.0;
        }
        
        if (paretoArchive.size() <= 2) {
            for (ParetoSolution sol : paretoArchive) {
                sol.crowdingDistance = Double.POSITIVE_INFINITY;
            }
            return;
        }
        
        crowdingOnObjective("makespan");
        crowdingOnObjective("energy");
        crowdingOnObjective("cost");
    }
    
    private void crowdingOnObjective(String objective) {
        paretoArchive.sort(Comparator.comparingDouble(s -> getObjective(s, objective)));
        
        double min = getObjective(paretoArchive.get(0), objective);
        double max = getObjective(paretoArchive.get(paretoArchive.size() - 1), objective);
        
        paretoArchive.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
        paretoArchive.get(paretoArchive.size() - 1).crowdingDistance = Double.POSITIVE_INFINITY;
        
        double range = (max - min);
        if (range == 0) range = 1.0;
        
        for (int i = 1; i < paretoArchive.size() - 1; i++) {
            if (Double.isInfinite(paretoArchive.get(i).crowdingDistance)) continue;
            
            double prev = getObjective(paretoArchive.get(i - 1), objective);
            double next = getObjective(paretoArchive.get(i + 1), objective);
            paretoArchive.get(i).crowdingDistance += (next - prev) / range;
        }
    }
    
    private double getObjective(ParetoSolution sol, String objective) {
        switch (objective) {
            case "makespan": return sol.makespan;
            case "energy": return sol.energy;
            case "cost": return sol.cost;
            default: return 0;
        }
    }
    
    private double calculateMakespan(int[] schedule, List<Cloudlet> cloudlets,
                                    List<Vm> vms, List<TaskSpec> tasks) {
        double[] vmFinishTime = new double[vms.size()];
        
        for (int i = 0; i < schedule.length; i++) {
            int vmIndex = schedule[i];
            double execTime = (double)tasks.get(i).instructions / vms.get(vmIndex).getMips();
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
            double execTime = (double)tasks.get(i).instructions / vms.get(vmIndex).getMips();
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
            
            double execTime = (double)task.instructions / vm.getMips();
            double computeCost = execTime * node.costPerCpuSecond;
            double memoryCost = (task.memoryRequired / 1024.0) * node.costPerMem * execTime;
            double bwCost = ((task.inputFileSize + task.outputFileSize) / 1024.0) * node.costPerBw;
            
            totalCost += computeCost + memoryCost + bwCost;
        }
        
        return totalCost;
    }
    
    private Map<Integer, Integer> applySolution(int[] schedule, List<Cloudlet> cloudlets, 
                                               List<Vm> vms) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = vms.get(schedule[i]);
            cloudlet.setVm(assignedVm);
            mapping.put((int)cloudlet.getId(), (int)assignedVm.getId());
        }
        
        return mapping;
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 