package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class MOWOAScheduling implements SchedulingAlgorithm {
    
    // MOWOA Parameters
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_ITERATIONS = 150;
    private static final double B = 1.0;
    
    // Objective weights
    private final double w1; // Makespan weight
    private final double w2; // Cost weight
    private final double w3; // Energy weight
    
    private Random random = new Random();
    
    public MOWOAScheduling(double makespanWeight, double costWeight, double energyWeight) {
        double sum = makespanWeight + costWeight + energyWeight;
        this.w1 = makespanWeight / sum;
        this.w2 = costWeight / sum;
        this.w3 = energyWeight / sum;
    }
    
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        System.out.printf("Starting Multi-Objective WOA (Weights: Makespan=%.2f, Cost=%.2f, Energy=%.2f)\n",
                         w1, w2, w3);
        
        int numTasks = cloudlets.size();
        int numVms = vms.size();
        
        // Initialize population
        Whale[] population = new Whale[POPULATION_SIZE];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            population[i] = new Whale(numTasks, numVms);
            population[i].evaluateFitness(cloudlets, vms, tasks, nodes);
        }
        
        // Find initial best whale
        Whale bestWhale = findBestWhale(population);
        
        System.out.printf("  Initial: Makespan=%.2f, Cost=%.2f, Energy=%.2f, Fitness=%.4f\n",
                         bestWhale.makespan, bestWhale.cost, bestWhale.energy, bestWhale.fitness);
        
        // Archive for Pareto solutions
        List<Whale> archive = new ArrayList<>();
        archive.add(bestWhale.clone());
        
        // Main WOA loop
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS);
            
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = random.nextDouble();
                double A = 2.0 * a * r - a;
                double C = 2.0 * r;
                double p = random.nextDouble();
                double l = (random.nextDouble() * 2) - 1;
                
                Whale newWhale;
                
                if (p < 0.5) {
                    if (Math.abs(A) < 1) {
                        // Exploitation: encircling prey
                        newWhale = encirclingPrey(population[i], bestWhale, A, C, numVms);
                    } else {
                        // Exploration: search for prey
                        Whale randomWhale = population[random.nextInt(POPULATION_SIZE)];
                        newWhale = encirclingPrey(population[i], randomWhale, A, C, numVms);
                    }
                } else {
                    // Exploitation: spiral bubble-net
                    newWhale = spiralAttack(population[i], bestWhale, l, numVms);
                }
                
                // Evaluate new whale
                newWhale.evaluateFitness(cloudlets, vms, tasks, nodes);
                
                // Update population if better
                if (newWhale.fitness < population[i].fitness) {
                    population[i] = newWhale;
                    
                    // Update best whale
                    if (newWhale.fitness < bestWhale.fitness) {
                        bestWhale = newWhale.clone();
                        System.out.printf("  Iteration %d: Makespan=%.2f, Cost=%.2f, Energy=%.2f, Fitness=%.4f\n",
                                        iter + 1, bestWhale.makespan, bestWhale.cost, 
                                        bestWhale.energy, bestWhale.fitness);
                    }
                    
                    // Update archive with non-dominated solutions
                    updateArchive(archive, newWhale);
                }
            }
        }
        
        System.out.println("✓ MOWOA completed");
        System.out.printf("  Best solution: Makespan=%.4f, Cost=%.4f, Energy=%.4f\n",
                         bestWhale.makespan, bestWhale.cost, bestWhale.energy);
        System.out.printf("  Archive size: %d Pareto-optimal solutions\n", archive.size());
        
        // Apply best solution
        Map<Integer, Integer> mapping = applySolution(bestWhale.solution, cloudlets, vms);
        return mapping;
    }
    
    /**
     * Inner class representing a whale (solution)
     */
    private class Whale {
        int[] solution;
        double makespan;
        double cost;
        double energy;
        double fitness;
        
        Whale(int numTasks, int numVms) {
            solution = new int[numTasks];
            for (int i = 0; i < numTasks; i++) {
                solution[i] = random.nextInt(numVms);
            }
        }
        
        Whale(int[] solution) {
            this.solution = solution.clone();
        }
        
        void evaluateFitness(List<Cloudlet> cloudlets, List<Vm> vms, 
                           List<TaskSpec> tasks, List<NodeSpec> nodes) {
            this.makespan = calculateMakespan(solution, cloudlets, vms, tasks);
            this.cost = calculateCost(solution, cloudlets, vms, tasks, nodes);
            this.energy = calculateEnergy(solution, cloudlets, vms, tasks, nodes);
            
            // Normalize and calculate weighted fitness
            this.fitness = w1 * makespan + w2 * cost + w3 * energy;
        }
        
        public Whale clone() {
        Whale cloned = new Whale(this.solution);
        cloned.makespan = this.makespan;
        cloned.cost = this.cost;
        cloned.energy = this.energy;
        cloned.fitness = this.fitness;
        return cloned;
    }
        
        boolean dominates(Whale other) {
            return (this.makespan <= other.makespan && this.cost <= other.cost && this.energy <= other.energy)
                   && (this.makespan < other.makespan || this.cost < other.cost || this.energy < other.energy);
        }
    }
    
    private Whale encirclingPrey(Whale current, Whale target, double A, double C, int numVms) {
        int[] newSolution = new int[current.solution.length];
        
        for (int i = 0; i < current.solution.length; i++) {
            double D = Math.abs(C * target.solution[i] - current.solution[i]);
            double newValue = target.solution[i] - A * D;
            
            newSolution[i] = (int)Math.round(newValue) % numVms;
            if (newSolution[i] < 0) newSolution[i] += numVms;
        }
        
        return new Whale(newSolution);
    }
    
    private Whale spiralAttack(Whale current, Whale best, double l, int numVms) {
        int[] newSolution = new int[current.solution.length];
        
        for (int i = 0; i < current.solution.length; i++) {
            double distance = Math.abs(best.solution[i] - current.solution[i]);
            double newValue = distance * Math.exp(B * l) * Math.cos(2 * Math.PI * l) + best.solution[i];
            
            newSolution[i] = (int)Math.round(newValue) % numVms;
            if (newSolution[i] < 0) newSolution[i] += numVms;
        }
        
        return new Whale(newSolution);
    }
    
    private Whale findBestWhale(Whale[] population) {
        Whale best = population[0];
        for (int i = 1; i < population.length; i++) {
            if (population[i].fitness < best.fitness) {
                best = population[i];
            }
        }
        return best.clone();
    }
    
    private void updateArchive(List<Whale> archive, Whale newWhale) {
        boolean isDominated = false;
        List<Whale> toRemove = new ArrayList<>();
        
        for (Whale whale : archive) {
            if (whale.dominates(newWhale)) {
                isDominated = true;
                break;
            }
            if (newWhale.dominates(whale)) {
                toRemove.add(whale);
            }
        }
        
        archive.removeAll(toRemove);
        
        if (!isDominated) {
            archive.add(newWhale.clone());
        }
    }
    
    private double calculateMakespan(int[] solution, List<Cloudlet> cloudlets, 
                                    List<Vm> vms, List<TaskSpec> tasks) {
        double[] vmFinishTime = new double[vms.size()];
        
        for (int i = 0; i < solution.length; i++) {
            int vmIndex = solution[i];
            double execTime = (double)tasks.get(i).instructions / vms.get(vmIndex).getMips();
            vmFinishTime[vmIndex] += execTime;
        }
        
        double makespan = 0;
        for (double time : vmFinishTime) {
            if (time > makespan) makespan = time;
        }
        return makespan;
    }
    
    private double calculateCost(int[] solution, List<Cloudlet> cloudlets, 
                                List<Vm> vms, List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double totalCost = 0.0;
        
        for (int i = 0; i < solution.length; i++) {
            int vmIndex = solution[i];
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
    
    private double calculateEnergy(int[] solution, List<Cloudlet> cloudlets, 
                                  List<Vm> vms, List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double[] vmActiveTime = new double[vms.size()];
        
        for (int i = 0; i < solution.length; i++) {
            int vmIndex = solution[i];
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
    
    private Map<Integer, Integer> applySolution(int[] solution, List<Cloudlet> cloudlets, List<Vm> vms) {
        Map<Integer, Integer> mapping = new HashMap<>();
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = vms.get(solution[i]);
            cloudlet.setVm(assignedVm);
            mapping.put((int)cloudlet.getId(), (int)assignedVm.getId());
        }
        
        return mapping;
    }
}
