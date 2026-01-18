package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class MORDAScheduling implements SchedulingAlgorithm {
    
    // RDA Parameters
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_ITERATIONS = 150;
    private static final double MALE_PERCENTAGE = 0.3;     // 30% males (stags)
    private static final double HAREM_PERCENTAGE = 0.6;    // 60% hinds in harems
    private static final double ROARING_PROBABILITY = 0.5;
    private static final double FIGHTING_PROBABILITY = 0.6;
    private static final double MATING_PROBABILITY = 0.7;
    private static final double MUTATION_RATE = 0.15;
    
    // Multi-objective weights
    private final double wMakespan;
    private final double wEnergy;
    private final double wCost;
    
    private Random rand = new Random();
    
    // Pareto archive
    private List<ParetoSolution> paretoArchive = new ArrayList<>();
    
    /**
     * Pareto solution entry
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
        
        ParetoSolution copy() {
            ParetoSolution copy = new ParetoSolution(this.schedule, this.makespan, 
                                                     this.energy, this.cost);
            copy.crowdingDistance = this.crowdingDistance;
            copy.fitness = this.fitness;
            return copy;
        }
    }
    
    /**
     * Red Deer individual
     */
    private static class RedDeer {
        int[] schedule;
        double makespan;
        double energy;
        double cost;
        double fitness;
        boolean isMale;
        int haremId;  // For hinds: which harem they belong to
        
        RedDeer(int[] schedule, boolean isMale) {
            this.schedule = schedule.clone();
            this.isMale = isMale;
            this.haremId = -1;
        }
        
        RedDeer copy() {
            RedDeer copy = new RedDeer(this.schedule, this.isMale);
            copy.makespan = this.makespan;
            copy.energy = this.energy;
            copy.cost = this.cost;
            copy.fitness = this.fitness;
            copy.haremId = this.haremId;
            return copy;
        }
    }
    
    public MORDAScheduling(double makespanWeight, double energyWeight, double costWeight) {
        double sum = makespanWeight + energyWeight + costWeight;
        this.wMakespan = makespanWeight / sum;
        this.wEnergy = energyWeight / sum;
        this.wCost = costWeight / sum;
    }
    
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms,
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        System.out.printf("Starting Multi-Objective Red Deer Algorithm (Weights: Makespan=%.2f, Energy=%.2f, Cost=%.2f)\n",
                         wMakespan, wEnergy, wCost);
        
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        int numMales = (int)(POPULATION_SIZE * MALE_PERCENTAGE);
        int numHinds = POPULATION_SIZE - numMales;
        int numHaremsHinds = (int)(numHinds * HAREM_PERCENTAGE);
        int numStags = Math.max(2, numMales / 2);  // Number of harems = number of dominant stags
        
        // Initialize population
        List<RedDeer> males = new ArrayList<>();
        List<RedDeer> hinds = new ArrayList<>();
        
        // Create males (stags)
        for (int i = 0; i < numMales; i++) {
            int[] schedule = generateRandomSchedule(numTasks, numVms);
            males.add(new RedDeer(schedule, true));
        }
        
        // Create hinds (females)
        for (int i = 0; i < numHinds; i++) {
            int[] schedule = generateRandomSchedule(numTasks, numVms);
            hinds.add(new RedDeer(schedule, false));
        }
        
        // Evaluate initial population
        double[] maxObjectives = new double[3];
        evaluatePopulation(males, cloudlets, vms, tasks, nodes, maxObjectives);
        evaluatePopulation(hinds, cloudlets, vms, tasks, nodes, maxObjectives);
        
        // Sort by fitness
        males.sort(Comparator.comparingDouble(d -> d.fitness));
        hinds.sort(Comparator.comparingDouble(d -> d.fitness));
        
        RedDeer bestDeer = males.get(0).copy();
        
        System.out.printf("  Initial: Makespan=%.2f, Energy=%.2f, Cost=%.2f, Archive=%d\n",
                         bestDeer.makespan, bestDeer.energy, bestDeer.cost, paretoArchive.size());
        
        // Main RDA Loop
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            
            // ========== PHASE 1: Roaring (Male Advertisement) ==========
            for (int i = 0; i < numMales; i++) {
                if (rand.nextDouble() < ROARING_PROBABILITY) {
                    int[] roared = roaring(males.get(i).schedule, numVms);
                    RedDeer newDeer = new RedDeer(roared, true);
                    evaluateDeer(newDeer, cloudlets, vms, tasks, nodes);
                    
                    // Update max objectives
                    updateMaxObjectives(newDeer, maxObjectives);
                    newDeer.fitness = scalarFitness(newDeer, maxObjectives);
                    
                    if (newDeer.fitness < males.get(i).fitness) {
                        males.set(i, newDeer);
                        addToParetoArchive(newDeer);
                    }
                }
            }
            
            // Sort males after roaring
            males.sort(Comparator.comparingDouble(d -> d.fitness));
            
            // ========== PHASE 2: Fighting (Male Competition) ==========
            List<RedDeer> winners = new ArrayList<>();
            
            for (int i = 0; i < numMales - 1; i += 2) {
                if (rand.nextDouble() < FIGHTING_PROBABILITY) {
                    RedDeer male1 = males.get(i);
                    RedDeer male2 = males.get(i + 1);
                    
                    // Fight: create offspring through crossover
                    int[] offspring1 = fighting(male1.schedule, male2.schedule);
                    int[] offspring2 = fighting(male2.schedule, male1.schedule);
                    
                    RedDeer child1 = new RedDeer(offspring1, true);
                    RedDeer child2 = new RedDeer(offspring2, true);
                    
                    evaluateDeer(child1, cloudlets, vms, tasks, nodes);
                    evaluateDeer(child2, cloudlets, vms, tasks, nodes);
                    
                    updateMaxObjectives(child1, maxObjectives);
                    updateMaxObjectives(child2, maxObjectives);
                    
                    child1.fitness = scalarFitness(child1, maxObjectives);
                    child2.fitness = scalarFitness(child2, maxObjectives);
                    
                    // Winner takes all: best of 4 (2 parents + 2 children)
                    List<RedDeer> contenders = Arrays.asList(male1, male2, child1, child2);
                    contenders.sort(Comparator.comparingDouble(d -> d.fitness));
                    
                    winners.add(contenders.get(0).copy());
                    winners.add(contenders.get(1).copy());
                    
                    addToParetoArchive(child1);
                    addToParetoArchive(child2);
                } else {
                    winners.add(males.get(i).copy());
                    if (i + 1 < numMales) {
                        winners.add(males.get(i + 1).copy());
                    }
                }
            }
            
            // Handle odd male
            if (numMales % 2 == 1) {
                winners.add(males.get(numMales - 1).copy());
            }
            
            males = winners;
            males.sort(Comparator.comparingDouble(d -> d.fitness));
            
            // Keep only top males
            if (males.size() > numMales) {
                males = males.subList(0, numMales);
            }
            
            // ========== PHASE 3: Forming Harems ==========
            // Top stags get harems
            List<RedDeer> haremHinds = hinds.subList(0, Math.min(numHaremsHinds, hinds.size()));
            
            // Assign hinds to harems (stags)
            int hindsPerHarem = Math.max(1, numHaremsHinds / numStags);
            for (int i = 0; i < haremHinds.size(); i++) {
                haremHinds.get(i).haremId = Math.min(i / hindsPerHarem, numStags - 1);
            }
            
            // ========== PHASE 4: Mating (Reproduction) ==========
            List<RedDeer> offspring = new ArrayList<>();
            
            for (int stagIdx = 0; stagIdx < Math.min(numStags, males.size()); stagIdx++) {
                RedDeer stag = males.get(stagIdx);
                
                // Get hinds in this stag's harem
                List<RedDeer> haremMembers = new ArrayList<>();
                for (RedDeer hind : haremHinds) {
                    if (hind.haremId == stagIdx) {
                        haremMembers.add(hind);
                    }
                }
                
                // Mate with hinds
                for (RedDeer hind : haremMembers) {
                    if (rand.nextDouble() < MATING_PROBABILITY) {
                        int[] child1Schedule = mating(stag.schedule, hind.schedule, numVms);
                        int[] child2Schedule = mating(hind.schedule, stag.schedule, numVms);
                        
                        RedDeer child1 = new RedDeer(child1Schedule, rand.nextBoolean());
                        RedDeer child2 = new RedDeer(child2Schedule, rand.nextBoolean());
                        
                        evaluateDeer(child1, cloudlets, vms, tasks, nodes);
                        evaluateDeer(child2, cloudlets, vms, tasks, nodes);
                        
                        updateMaxObjectives(child1, maxObjectives);
                        updateMaxObjectives(child2, maxObjectives);
                        
                        child1.fitness = scalarFitness(child1, maxObjectives);
                        child2.fitness = scalarFitness(child2, maxObjectives);
                        
                        offspring.add(child1);
                        offspring.add(child2);
                        
                        addToParetoArchive(child1);
                        addToParetoArchive(child2);
                    }
                }
            }
            
            // ========== PHASE 5: Selection (Survival of the Fittest) ==========
            // Replace worst hinds with offspring
            if (!offspring.isEmpty()) {
                offspring.sort(Comparator.comparingDouble(d -> d.fitness));
                
                int numToReplace = Math.min(offspring.size(), hinds.size() / 2);
                for (int i = 0; i < numToReplace; i++) {
                    int worstIdx = hinds.size() - 1 - i;
                    if (worstIdx >= 0 && i < offspring.size()) {
                        if (offspring.get(i).isMale) {
                            // Replace worst male if offspring is male
                            int worstMaleIdx = males.size() - 1;
                            if (offspring.get(i).fitness < males.get(worstMaleIdx).fitness) {
                                males.set(worstMaleIdx, offspring.get(i));
                            }
                        } else {
                            hinds.set(worstIdx, offspring.get(i));
                        }
                    }
                }
            }
            
            // Re-sort populations
            males.sort(Comparator.comparingDouble(d -> d.fitness));
            hinds.sort(Comparator.comparingDouble(d -> d.fitness));
            
            // Update best deer
            bestDeer = males.get(0).copy();
            
            if ((iter + 1) % 20 == 0) {
                System.out.printf("  Iteration %d: Best fitness=%.4f, Archive=%d\n",
                                iter + 1, bestDeer.fitness, paretoArchive.size());
            }
        }
        
        // Select final solution from Pareto archive
        computeCrowdingDistance();
        paretoArchive.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
        
        ParetoSolution finalSolution = paretoArchive.isEmpty() ? 
            new ParetoSolution(bestDeer.schedule, bestDeer.makespan, bestDeer.energy, bestDeer.cost) :
            paretoArchive.get(0);
        
        System.out.println("✓ Multi-Objective RDA completed");
        System.out.printf("  Final archive: %d Pareto-optimal solutions\n", paretoArchive.size());
        System.out.printf("  Selected: Makespan=%.4f, Energy=%.4f, Cost=%.4f\n",
                         finalSolution.makespan, finalSolution.energy, finalSolution.cost);
        
        // Apply solution
        return applySolution(finalSolution.schedule, cloudlets, vms);
    }
    
    /**
     * Generate random schedule
     */
    private int[] generateRandomSchedule(int numTasks, int numVms) {
        int[] schedule = new int[numTasks];
        for (int i = 0; i < numTasks; i++) {
            schedule[i] = rand.nextInt(numVms);
        }
        return schedule;
    }
    
    /**
     * Roaring: Local search/mutation to attract females
     */
    private int[] roaring(int[] schedule, int numVms) {
        int[] roared = schedule.clone();
        
        // Multiple position mutations
        int numMutations = 1 + rand.nextInt(3); // 1-3 mutations
        for (int m = 0; m < numMutations; m++) {
            int pos = rand.nextInt(schedule.length);
            roared[pos] = rand.nextInt(numVms);
        }
        
        return roared;
    }
    
    /**
     * Fighting: Crossover between two males with mutation
     */
    private int[] fighting(int[] male1, int[] male2) {
        int[] offspring = new int[male1.length];
        
        // Two-point crossover
        int point1 = rand.nextInt(male1.length);
        int point2 = rand.nextInt(male1.length);
        
        if (point1 > point2) {
            int temp = point1;
            point1 = point2;
            point2 = temp;
        }
        
        for (int i = 0; i < male1.length; i++) {
            if (i < point1 || i >= point2) {
                offspring[i] = male1[i];
            } else {
                offspring[i] = male2[i];
            }
        }
        
        return offspring;
    }
    
    /**
     * Mating: Reproduction between stag and hind
     */
    private int[] mating(int[] parent1, int[] parent2, int numVms) {
        int[] child = new int[parent1.length];
        
        // Uniform crossover
        for (int i = 0; i < parent1.length; i++) {
            if (rand.nextDouble() < 0.5) {
                child[i] = parent1[i];
            } else {
                child[i] = parent2[i];
            }
        }
        
        // Mutation
        for (int i = 0; i < child.length; i++) {
            if (rand.nextDouble() < MUTATION_RATE) {
                child[i] = rand.nextInt(numVms);
            }
        }
        
        return child;
    }
    
    private void evaluatePopulation(List<RedDeer> population, List<Cloudlet> cloudlets, 
                                   List<Vm> vms, List<TaskSpec> tasks, List<NodeSpec> nodes,
                                   double[] maxObjectives) {
        for (RedDeer deer : population) {
            evaluateDeer(deer, cloudlets, vms, tasks, nodes);
            updateMaxObjectives(deer, maxObjectives);
            deer.fitness = scalarFitness(deer, maxObjectives);
            addToParetoArchive(deer);
        }
    }
    
    private void evaluateDeer(RedDeer deer, List<Cloudlet> cloudlets, List<Vm> vms,
                             List<TaskSpec> tasks, List<NodeSpec> nodes) {
        deer.makespan = calculateMakespan(deer.schedule, cloudlets, vms, tasks);
        deer.energy = calculateEnergy(deer.schedule, cloudlets, vms, tasks, nodes);
        deer.cost = calculateCost(deer.schedule, cloudlets, vms, tasks, nodes);
    }
    
    private void updateMaxObjectives(RedDeer deer, double[] maxObjectives) {
        maxObjectives[0] = Math.max(maxObjectives[0], deer.makespan);
        maxObjectives[1] = Math.max(maxObjectives[1], deer.energy);
        maxObjectives[2] = Math.max(maxObjectives[2], deer.cost);
    }
    
    private double scalarFitness(RedDeer deer, double[] maxObjectives) {
        double normMakespan = deer.makespan / Math.max(1e-9, maxObjectives[0]);
        double normEnergy = deer.energy / Math.max(1e-9, maxObjectives[1]);
        double normCost = deer.cost / Math.max(1e-9, maxObjectives[2]);
        
        return wMakespan * normMakespan + wEnergy * normEnergy + wCost * normCost;
    }
    
    private void addToParetoArchive(RedDeer deer) {
        addToParetoArchive(deer.schedule, deer.makespan, deer.energy, deer.cost);
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
