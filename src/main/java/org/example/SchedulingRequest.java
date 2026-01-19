package org.example;

public class SchedulingRequest {
    private String algname;
    private String dataset;
    private boolean is_eval_process;

    // Constructors
    public SchedulingRequest() {}

    public SchedulingRequest(String algname, String dataset, boolean is_eval_process) {
        this.algname = algname;
        this.dataset = dataset;
        this.is_eval_process = is_eval_process;
    }

    // Getters and Setters
    public String getAlgname() {
        return algname;
    }

    public void setAlgname(String algname) {
        this.algname = algname;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public boolean isIs_eval_process() {
        return is_eval_process;
    }

    public void setIs_eval_process(boolean is_eval_process) {
        this.is_eval_process = is_eval_process;
    }
}
