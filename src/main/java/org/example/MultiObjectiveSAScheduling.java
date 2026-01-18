package org.example;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class MultiObjectiveSAScheduling implements SchedulingAlgorithm {
    
    private static final double INITIAL_TEMPERATURE = 1000.0;
    private static final double COOLING_RATE = 0.95;
    private static final double MIN_TEMPERATURE = 0.01;
    private static final int ITERATIONS_PER_TEMP = 100;
    
    // Weights for objectives (makespan, cost, energy)
    private final double w1;
    private final double w2;
    private final double w3;
    
    public MultiObjectiveSAScheduling(double makespanWeight, double costWeight, double energyWeight) {
        // Normalize weights
        double sum = makespanWeight + costWeight + energyWeight;
        this.w1 = makespanWeight / sum;
        this.w2 = costWeight / sum;
        this.w3 = energyWeight / sum;
    }
    
    @Override
    public Map<Integer, Integer> schedule(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        System.out.printf("Starting Multi-Objective SA (Weights: Makespan=%.2f, Cost=%.2f, Energy=%.2f)\n",
                         w1, w2, w3);
        
        // Initialize solution
        int[] currentSolution = generateInitialSolution(cloudlets, vms, tasks, nodes);
        double[] currentObjectives = evaluateAllObjectives(currentSolution, cloudlets, vms, tasks, nodes);
        double currentCost = weightedSum(currentObjectives);
        
        // Best solution tracking
        int[] bestSolution = currentSolution.clone();
        double[] bestObjectives = currentObjectives.clone();
        double bestCost = currentCost;
        
        // Normalization factors (for scaling objectives)
        double[] maxObjectives = new double[3];
        System.arraycopy(currentObjectives, 0, maxObjectives, 0, 3);
        
        // SA main loop
        double temperature = INITIAL_TEMPERATURE;
        int iteration = 0;
        
        while (temperature > MIN_TEMPERATURE) {
            for (int i = 0; i < ITERATIONS_PER_TEMP; i++) {
                int[] neighborSolution = generateNeighbor(currentSolution, cloudlets.size(), vms.size());
                double[] neighborObjectives = evaluateAllObjectives(neighborSolution, cloudlets, vms, tasks, nodes);
                
                // Update max values for normalization
                for (int j = 0; j < 3; j++) {
                    if (neighborObjectives[j] > maxObjectives[j]) {
                        maxObjectives[j] = neighborObjectives[j];
                    }
                }
                
                // Normalize and calculate weighted sum
                double[] normalizedCurrent = normalize(currentObjectives, maxObjectives);
                double[] normalizedNeighbor = normalize(neighborObjectives, maxObjectives);
                
                double neighborCost = weightedSum(normalizedNeighbor);
                double normalizedCurrentCost = weightedSum(normalizedCurrent);
                
                double delta = neighborCost - normalizedCurrentCost;
                double acceptanceProbability = (delta < 0) ? 1.0 : Math.exp(-delta / temperature);
                
                if (Math.random() < acceptanceProbability) {
                    currentSolution = neighborSolution;
                    currentObjectives = neighborObjectives;
                    currentCost = neighborCost;
                    
                    if (currentCost < bestCost) {
                        bestSolution = currentSolution.clone();
                        bestObjectives = currentObjectives.clone();
                        bestCost = currentCost;
                        System.out.printf("  Iter %d: Makespan=%.2f, Cost=%.2f, Energy=%.2f (T=%.2f)\n",
                                        iteration, bestObjectives[0], bestObjectives[1], 
                                        bestObjectives[2], temperature);
                    }
                }
                
                iteration++;
            }
            
            temperature *= COOLING_RATE;
        }
        
        System.out.printf("✓ Multi-Objective SA completed after %d iterations\n", iteration);
        System.out.printf("  Final: Makespan=%.4f, Cost=%.4f, Energy=%.4f\n",
                        bestObjectives[0], bestObjectives[1], bestObjectives[2]);
        
        // Apply best solution
        Map<Integer, Integer> mapping = new HashMap<>();
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = vms.get(bestSolution[i]);
            cloudlet.setVm(assignedVm);
            mapping.put((int)cloudlet.getId(), (int)assignedVm.getId());
        }
        
        return mapping;
    }
    
    private int[] generateInitialSolution(List<Cloudlet> cloudlets, List<Vm> vms, 
                                         List<TaskSpec> tasks, List<NodeSpec> nodes) {
        int[] solution = new int[cloudlets.size()];
        double[] vmReadyTime = new double[vms.size()];
        
        for (int i = 0; i < cloudlets.size(); i++) {
            int bestVm = 0;
            double minCompletionTime = Double.MAX_VALUE;
            
            for (int j = 0; j < vms.size(); j++) {
                double execTime = (double)tasks.get(i).instructions / vms.get(j).getMips();
                double completionTime = vmReadyTime[j] + execTime;
                
                if (completionTime < minCompletionTime) {
                    minCompletionTime = completionTime;
                    bestVm = j;
                }
            }
            
            solution[i] = bestVm;
            double execTime = (double)tasks.get(i).instructions / vms.get(bestVm).getMips();
            vmReadyTime[bestVm] += execTime;
        }
        
        return solution;
    }
    
    private int[] generateNeighbor(int[] currentSolution, int numTasks, int numVms) {
        int[] neighbor = currentSolution.clone();
        Random rand = new Random();
        
        if (rand.nextDouble() < 0.5 && numTasks > 1) {
            int task1 = rand.nextInt(numTasks);
            int task2 = rand.nextInt(numTasks);
            int temp = neighbor[task1];
            neighbor[task1] = neighbor[task2];
            neighbor[task2] = temp;
        } else {
            int task = rand.nextInt(numTasks);
            int newVm = rand.nextInt(numVms);
            neighbor[task] = newVm;
        }
        
        return neighbor;
    }
    
    private double[] evaluateAllObjectives(int[] solution, List<Cloudlet> cloudlets, 
                                          List<Vm> vms, List<TaskSpec> tasks, List<NodeSpec> nodes) {
        double[] objectives = new double[3];
        objectives[0] = calculateMakespan(solution, cloudlets, vms, tasks);
        objectives[1] = calculateCost(solution, cloudlets, vms, tasks, nodes);
        objectives[2] = calculateEnergy(solution, cloudlets, vms, tasks, nodes);
        return objectives;
    }
    
    private double[] normalize(double[] values, double[] maxValues) {
        double[] normalized = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = maxValues[i] > 0 ? values[i] / maxValues[i] : 0;
        }
        return normalized;
    }
    
    private double weightedSum(double[] objectives) {
        return w1 * objectives[0] + w2 * objectives[1] + w3 * objectives[2];
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
}
