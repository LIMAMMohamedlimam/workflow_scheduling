package org.example;

public class TaskSpec {
    String taskId;
    long instructions;
    long memoryRequired;
    long inputFileSize;
    long outputFileSize;

    public TaskSpec(String taskId, long instructions, long memory, long inputSize, long outputSize) {
        this.taskId = taskId;
        this.instructions = instructions;
        this.memoryRequired = memory;
        this.inputFileSize = inputSize;
        this.outputFileSize = outputSize;
    }
}