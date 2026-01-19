import argparse
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
import warnings
warnings.filterwarnings('ignore')

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def get_pareto_front(objectives):
    """Extract non-dominated solutions"""
    n = len(objectives)
    if n == 0:
        return objectives, np.array([])
    
    is_pareto = np.ones(n, dtype=bool)
    
    for i in range(n):
        if not is_pareto[i]:
            continue
        for j in range(n):
            if i != j and is_pareto[i]:
                if np.all(objectives[j] <= objectives[i]) and np.any(objectives[j] < objectives[i]):
                    is_pareto[i] = False
                    break
    
    pareto_indices = np.where(is_pareto)[0]
    return objectives[pareto_indices], pareto_indices


def calculate_hypervolume_3d(pareto_front, ref_point):
    """Calculate hypervolume indicator (higher is better)"""
    if len(pareto_front) == 0:
        return 0.0
    
    sorted_front = pareto_front[np.argsort(pareto_front[:, 0])]
    hv = 0.0
    n = len(sorted_front)
    
    for i in range(n):
        if i < n - 1:
            width = sorted_front[i+1, 0] - sorted_front[i, 0]
        else:
            width = ref_point[0] - sorted_front[i, 0]
        
        height = ref_point[1] - sorted_front[i, 1]
        depth = ref_point[2] - sorted_front[i, 2]
        
        if width > 0 and height > 0 and depth > 0:
            hv += width * height * depth
    
    return hv


def calculate_spacing(pareto_front):
    """Calculate spacing metric (lower is better)"""
    n = len(pareto_front)
    if n < 2:
        return 0.0
    
    distances = []
    for i in range(n):
        min_dist = float('inf')
        for j in range(n):
            if i != j:
                dist = np.linalg.norm(pareto_front[i] - pareto_front[j])
                min_dist = min(min_dist, dist)
        distances.append(min_dist)
    
    d_mean = np.mean(distances)
    spacing = np.sqrt(np.sum((np.array(distances) - d_mean)**2) / (n - 1))
    return spacing


def calculate_igd(pareto_front, reference_front):
    """Calculate IGD metric (lower is better)"""
    if len(reference_front) == 0 or len(pareto_front) == 0:
        return float('inf')
    
    total_dist = 0.0
    for ref_point in reference_front:
        min_dist = np.min([np.linalg.norm(ref_point - sol) for sol in pareto_front])
        total_dist += min_dist
    
    return total_dist / len(reference_front)


# ============================================================================
# MAIN EVALUATOR CLASS
# ============================================================================

class MultiAlgorithmEvaluator:
    """Complete evaluation system for multi-algorithm multi-dataset comparison"""
    
    def __init__(self, csv_file):
        if isinstance(csv_file, str):
            self.df = pd.read_csv(csv_file)
        else:
            self.df = csv_file
        
        required_cols = ['algorithm_name', 'makespan', 'cost', 'energy', 'dataset_used']
        if not all(col in self.df.columns for col in required_cols):
            raise ValueError(f"CSV must contain columns: {required_cols}")
        
        self.algorithms = self.df['algorithm_name'].unique()
        self.datasets = self.df['dataset_used'].unique()
        self.objective_cols = ['makespan', 'cost', 'energy']
        
        print(f"✅ Data loaded: {len(self.algorithms)} algorithms, {len(self.datasets)} datasets, {len(self.df)} runs")
    
    def extract_pareto_fronts(self):
        """Extract Pareto fronts for each algorithm-dataset combination"""
        pareto_data = {}
        
        for dataset in self.datasets:
            pareto_data[dataset] = {}
            dataset_df = self.df[self.df['dataset_used'] == dataset]
            
            for algorithm in self.algorithms:
                algo_data = dataset_df[dataset_df['algorithm_name'] == algorithm]
                
                if len(algo_data) > 0:
                    objectives = algo_data[self.objective_cols].values
                    pareto_front, _ = get_pareto_front(objectives)
                    pareto_data[dataset][algorithm] = pareto_front
                else:
                    pareto_data[dataset][algorithm] = np.array([]).reshape(0, 3)
        
        self.pareto_data = pareto_data
        return pareto_data
    
    def calculate_metrics(self):
        """Calculate all performance metrics"""
        if not hasattr(self, 'pareto_data'):
            self.extract_pareto_fronts()
        
        metrics_list = []
        
        for dataset in self.datasets:
            dataset_df = self.df[self.df['dataset_used'] == dataset]
            all_objectives = dataset_df[self.objective_cols].values
            
            # Reference point: 1.1 * max values
            ref_point = np.max(all_objectives, axis=0) * 1.1
            
            # Combined Pareto front for IGD
            all_pareto = [self.pareto_data[dataset][algo] 
                         for algo in self.algorithms 
                         if len(self.pareto_data[dataset][algo]) > 0]
            
            if all_pareto:
                combined_pareto, _ = get_pareto_front(np.vstack(all_pareto))
            else:
                combined_pareto = np.array([]).reshape(0, 3)
            
            # Calculate metrics for each algorithm
            for algorithm in self.algorithms:
                pareto = self.pareto_data[dataset][algorithm]
                
                if len(pareto) > 0:
                    hv = calculate_hypervolume_3d(pareto, ref_point)
                    spacing = calculate_spacing(pareto)
                    igd = calculate_igd(pareto, combined_pareto)
                    
                    metrics_list.append({
                        'Dataset': dataset,
                        'Algorithm': algorithm,
                        'Hypervolume': hv,
                        'Spacing': spacing,
                        'IGD': igd,
                        'N_Solutions': len(pareto),
                        'Best_Makespan': np.min(pareto[:, 0]),
                        'Best_Cost': np.min(pareto[:, 1]),
                        'Best_Energy': np.min(pareto[:, 2]),
                    })
                else:
                    metrics_list.append({
                        'Dataset': dataset,
                        'Algorithm': algorithm,
                        'Hypervolume': 0.0,
                        'Spacing': 0.0,
                        'IGD': float('inf'),
                        'N_Solutions': 0,
                        'Best_Makespan': np.nan,
                        'Best_Cost': np.nan,
                        'Best_Energy': np.nan,
                    })
        
        self.metrics_df = pd.DataFrame(metrics_list)
        return self.metrics_df
    
    def create_visualizations(self, output_dir='results'):
        """Create all visualizations"""
        import os
        os.makedirs(output_dir, exist_ok=True)
        
        colors = ['red', 'blue', 'green', 'purple', 'orange', 'brown', 'pink', 'gray']
        markers = ['o', 's', '^', 'D', 'v', '<', '>', 'p']
        
        # Create 3D Pareto fronts for each dataset
        for dataset in self.datasets:
            fig = plt.figure(figsize=(16, 12))
            
            # View 1
            ax1 = fig.add_subplot(221, projection='3d')
            for idx, (algo, pareto) in enumerate(self.pareto_data[dataset].items()):
                if len(pareto) > 0:
                    ax1.scatter(pareto[:, 0], pareto[:, 1], pareto[:, 2],
                               c=colors[idx % len(colors)], 
                               marker=markers[idx % len(markers)],
                               s=120, alpha=0.8, edgecolors='black', 
                               linewidths=1, label=algo)
            
            ax1.set_xlabel('Makespan (s)', fontsize=11, fontweight='bold')
            ax1.set_ylabel('Cost (€)', fontsize=11, fontweight='bold')
            ax1.set_zlabel('Energy (J)', fontsize=11, fontweight='bold')
            ax1.set_title(f'3D Pareto Front - {dataset}', fontsize=13, fontweight='bold')
            ax1.legend(fontsize=9)
            ax1.view_init(elev=20, azim=45)
            ax1.grid(True, alpha=0.3)
            
            # View 2
            ax2 = fig.add_subplot(222, projection='3d')
            for idx, (algo, pareto) in enumerate(self.pareto_data[dataset].items()):
                if len(pareto) > 0:
                    ax2.scatter(pareto[:, 0], pareto[:, 1], pareto[:, 2],
                               c=colors[idx % len(colors)], 
                               marker=markers[idx % len(markers)],
                               s=120, alpha=0.8, edgecolors='black', 
                               linewidths=1, label=algo)
            
            ax2.set_xlabel('Makespan (s)', fontsize=11, fontweight='bold')
            ax2.set_ylabel('Cost (€)', fontsize=11, fontweight='bold')
            ax2.set_zlabel('Energy (J)', fontsize=11, fontweight='bold')
            ax2.set_title(f'3D Pareto Front - {dataset} (View 2)', fontsize=13, fontweight='bold')
            ax2.legend(fontsize=9)
            ax2.view_init(elev=30, azim=135)
            ax2.grid(True, alpha=0.3)
            
            # 2D: Makespan vs Cost
            ax3 = fig.add_subplot(223)
            for idx, (algo, pareto) in enumerate(self.pareto_data[dataset].items()):
                if len(pareto) > 0:
                    ax3.scatter(pareto[:, 0], pareto[:, 1],
                               c=colors[idx % len(colors)], 
                               marker=markers[idx % len(markers)],
                               s=120, alpha=0.8, edgecolors='black', 
                               linewidths=1, label=algo)
            
            ax3.set_xlabel('Makespan (s)', fontsize=11, fontweight='bold')
            ax3.set_ylabel('Cost (€)', fontsize=11, fontweight='bold')
            ax3.set_title('2D: Makespan vs Cost', fontsize=12, fontweight='bold')
            ax3.legend(fontsize=9)
            ax3.grid(True, alpha=0.3)
            
            # 2D: Makespan vs Energy
            ax4 = fig.add_subplot(224)
            for idx, (algo, pareto) in enumerate(self.pareto_data[dataset].items()):
                if len(pareto) > 0:
                    ax4.scatter(pareto[:, 0], pareto[:, 2],
                               c=colors[idx % len(colors)], 
                               marker=markers[idx % len(markers)],
                               s=120, alpha=0.8, edgecolors='black', 
                               linewidths=1, label=algo)
            
            ax4.set_xlabel('Makespan (s)', fontsize=11, fontweight='bold')
            ax4.set_ylabel('Energy (J)', fontsize=11, fontweight='bold')
            ax4.set_title('2D: Makespan vs Energy', fontsize=12, fontweight='bold')
            ax4.legend(fontsize=9)
            ax4.grid(True, alpha=0.3)
            
            plt.tight_layout()
            plt.savefig(f'{output_dir}/pareto_3d_{dataset}.png', dpi=300, bbox_inches='tight')
            plt.close()
            print(f"✅ Saved: {output_dir}/pareto_3d_{dataset}.png")
        
        print(f"\n✅ All visualizations saved to: {output_dir}/")
        
    def rank_algorithms(self, weights=None, by_dataset=True, aggregate='mean'):
        """
        Rank algorithms from best to worst using a composite score.
        - Hypervolume: higher is better
        - IGD, Spacing: lower is better

        weights: dict like {'Hypervolume':0.5, 'IGD':0.3, 'Spacing':0.2}
        by_dataset: True => ranking per dataset + global ranking
        aggregate: 'mean' or 'median' (how to aggregate across datasets for global rank)
        """
        if not hasattr(self, 'metrics_df'):
            self.calculate_metrics()

        if weights is None:
            weights = {'Hypervolume': 0.5, 'IGD': 0.3, 'Spacing': 0.2}

        # Copy to avoid modifying original
        df = self.metrics_df.copy()

        # Replace inf with NaN for safe normalization, keep track later
        df['IGD'] = df['IGD'].replace([np.inf, -np.inf], np.nan)

        def minmax(s):
            s = s.astype(float)
            s_min, s_max = np.nanmin(s.values), np.nanmax(s.values)
            if np.isclose(s_max, s_min) or np.isnan(s_min) or np.isnan(s_max):
                # all equal or empty -> neutral 0.5
                return pd.Series(np.full(len(s), 0.5), index=s.index)
            return (s - s_min) / (s_max - s_min)

        ranked_outputs = {}

        # Rank per dataset (recommended)
        if by_dataset:
            per_dataset_rows = []
            for dataset in self.datasets:
                sub = df[df['Dataset'] == dataset].copy()

                # Normalize within dataset
                sub['HV_norm'] = minmax(sub['Hypervolume'])
                sub['IGD_norm'] = minmax(sub['IGD'])
                sub['SP_norm']  = minmax(sub['Spacing'])

                # Higher is better for all:
                sub['IGD_good'] = 1.0 - sub['IGD_norm']
                sub['SP_good']  = 1.0 - sub['SP_norm']

                sub['Score'] = (
                    weights.get('Hypervolume', 0.0) * sub['HV_norm'] +
                    weights.get('IGD', 0.0)         * sub['IGD_good'] +
                    weights.get('Spacing', 0.0)     * sub['SP_good']
                )

                # Penalize algorithms with no solutions
                sub.loc[sub['N_Solutions'] == 0, 'Score'] = -1.0

                sub = sub.sort_values('Score', ascending=False)
                sub['Rank'] = range(1, len(sub) + 1)

                ranked_outputs[dataset] = sub[['Dataset','Algorithm','Score','Rank','Hypervolume','IGD','Spacing','N_Solutions']]
                per_dataset_rows.append(sub[['Dataset','Algorithm','Score']])

            # Global ranking across datasets
            all_scores = pd.concat(per_dataset_rows, ignore_index=True)

            if aggregate == 'median':
                global_scores = all_scores.groupby('Algorithm', as_index=False)['Score'].median()
            else:
                global_scores = all_scores.groupby('Algorithm', as_index=False)['Score'].mean()

            global_scores = global_scores.sort_values('Score', ascending=False)
            global_scores['Rank'] = range(1, len(global_scores) + 1)

            ranked_outputs['GLOBAL'] = global_scores

        else:
            # One ranking using global normalization (less recommended if datasets scales differ)
            df['HV_norm'] = minmax(df['Hypervolume'])
            df['IGD_norm'] = minmax(df['IGD'])
            df['SP_norm']  = minmax(df['Spacing'])
            df['Score'] = (
                weights.get('Hypervolume', 0.0) * df['HV_norm'] +
                weights.get('IGD', 0.0)         * (1.0 - df['IGD_norm']) +
                weights.get('Spacing', 0.0)     * (1.0 - df['SP_norm'])
            )
            df.loc[df['N_Solutions'] == 0, 'Score'] = -1.0
            df = df.sort_values('Score', ascending=False)
            df['Rank'] = range(1, len(df) + 1)
            ranked_outputs['GLOBAL'] = df[['Algorithm','Score','Rank']]

        self.ranking = ranked_outputs
        return ranked_outputs


    def plot_algorithm_ranking(self, output_dir='results', top_k=None):
        """
        Plot ranking scores:
        - Per dataset: bar chart of Score by algorithm
        - Global: bar chart of aggregated score
        """
        import os
        os.makedirs(output_dir, exist_ok=True)

        if not hasattr(self, 'ranking'):
            self.rank_algorithms()

        # Per-dataset plots
        for dataset in self.datasets:
            if dataset not in self.ranking:
                continue
            sub = self.ranking[dataset].copy()
            if top_k is not None:
                sub = sub.nsmallest(top_k, 'Rank')  # keep best ranks

            plt.figure(figsize=(12, 6))
            plt.bar(sub['Algorithm'], sub['Score'])
            plt.xticks(rotation=45, ha='right')
            plt.ylabel('Composite Score (higher = better)')
            plt.title(f'Algorithm Ranking (Score) - {dataset}')
            plt.grid(True, axis='y', alpha=0.3)
            plt.tight_layout()
            path = f"{output_dir}/ranking_{dataset}.png"
            plt.savefig(path, dpi=300, bbox_inches='tight')
            plt.close()
            print(f"✅ Saved: {path}")

        # Global plot
        if 'GLOBAL' in self.ranking and isinstance(self.ranking['GLOBAL'], pd.DataFrame):
            g = self.ranking['GLOBAL'].copy()
            if top_k is not None:
                g = g.nsmallest(top_k, 'Rank')

            plt.figure(figsize=(12, 6))
            plt.bar(g['Algorithm'], g['Score'])
            plt.xticks(rotation=45, ha='right')
            plt.ylabel('Aggregated Score (higher = better)')
            plt.title('Global Algorithm Ranking (Aggregated Score)')
            plt.grid(True, axis='y', alpha=0.3)
            plt.tight_layout()
            path = f"{output_dir}/ranking_GLOBAL.png"
            plt.savefig(path, dpi=300, bbox_inches='tight')
            plt.close()
            print(f"✅ Saved: {path}")


# ============================================================================
# USAGE EXAMPLE
# ============================================================================



# # Load your CSV file
# evaluator = MultiAlgorithmEvaluator('workflow_metrics.csv')

# # Extract Pareto fronts
# evaluator.extract_pareto_fronts()

# # Calculate metrics
# metrics_df = evaluator.calculate_metrics()

# # Save metrics
# metrics_df.to_csv('performance_metrics.csv', index=False)
# print("\n✅ Metrics saved to: performance_metrics.csv")

# # Display results
# print("\n" + "="*80)
# print("PERFORMANCE METRICS SUMMARY")
# print("="*80)
# for dataset in evaluator.datasets:
#     print(f"\n{dataset}:")
#     dataset_metrics = metrics_df[metrics_df['Dataset'] == dataset].sort_values('Hypervolume', ascending=False)
#     print(dataset_metrics[['Algorithm', 'Hypervolume', 'Spacing', 'IGD', 'N_Solutions']].to_string(index=False))

# # Create visualizations
# evaluator.create_visualizations(output_dir='results')



# # Rank algorithms
# ranking = evaluator.rank_algorithms(
#     weights={'Hypervolume': 0.5, 'IGD': 0.3, 'Spacing': 0.2},
#     by_dataset=True,
#     aggregate='mean'
# )

# print("\n" + "="*80)
# print("ALGORITHM RANKING (BEST -> WORST)")
# print("="*80)

# for dataset in evaluator.datasets:
#     print(f"\n{dataset}:")
#     print(ranking[dataset][['Rank','Algorithm','Score','Hypervolume','IGD','Spacing','N_Solutions']].to_string(index=False))

# print("\nGLOBAL:")
# print(ranking['GLOBAL'].to_string(index=False))

# # Visualize ranking
# evaluator.plot_algorithm_ranking(output_dir='results', top_k=None)

import argparse
import os

def main():
    parser = argparse.ArgumentParser(
        description="Evaluate multi-algorithm workflow scheduling results"
    )

    parser.add_argument(
        "input_csv",
        nargs="?",
        default="workflow_metrics.csv",
        help="Path to input CSV file (default: workflow_metrics.csv)"
    )

    parser.add_argument(
        "output_csv",
        nargs="?",
        default="performance_metrics.csv",
        help="Path to output metrics CSV file (default: performance_metrics.csv)"
    )

    parser.add_argument(
        "output_dir",
        nargs="?",
        default="results",
        help="Directory for plots and visual outputs (default: results/)"
    )

    args = parser.parse_args()

    # Fallback safety
    input_csv = args.input_csv or "workflow_metrics.csv"
    output_csv = args.output_csv or "performance_metrics.csv"
    output_dir = args.output_dir or "results"

    os.makedirs(output_dir, exist_ok=True)

    print(f"📂 Input CSV   : {input_csv}")
    print(f"💾 Output CSV  : {output_csv}")
    print(f"📊 Output Dir  : {output_dir}")

    # Load input CSV
    evaluator = MultiAlgorithmEvaluator(input_csv)

    # Extract Pareto fronts
    evaluator.extract_pareto_fronts()

    # Calculate metrics
    metrics_df = evaluator.calculate_metrics()

    # Save metrics
    metrics_df.to_csv(output_csv, index=False)
    print(f"\n✅ Metrics saved to: {output_csv}")

    # Display results
    print("\n" + "=" * 80)
    print("PERFORMANCE METRICS SUMMARY")
    print("=" * 80)

    for dataset in evaluator.datasets:
        print(f"\n{dataset}:")
        dataset_metrics = (
            metrics_df[metrics_df["Dataset"] == dataset]
            .sort_values("Hypervolume", ascending=False)
        )
        print(
            dataset_metrics[
                ["Algorithm", "Hypervolume", "Spacing", "IGD", "N_Solutions"]
            ].to_string(index=False)
        )

    # Create visualizations
    evaluator.create_visualizations(output_dir=output_dir)

    # Rank algorithms
    ranking = evaluator.rank_algorithms(
        weights={"Hypervolume": 0.5, "IGD": 0.3, "Spacing": 0.2},
        by_dataset=True,
        aggregate="mean"
    )

    print("\n" + "=" * 80)
    print("ALGORITHM RANKING (BEST -> WORST)")
    print("=" * 80)

    for dataset in evaluator.datasets:
        print(f"\n{dataset}:")
        print(
            ranking[dataset][
                ["Rank", "Algorithm", "Score", "Hypervolume", "IGD", "Spacing", "N_Solutions"]
            ].to_string(index=False)
        )

    print("\nGLOBAL:")
    print(ranking["GLOBAL"].to_string(index=False))

    # Visualize ranking
    evaluator.plot_algorithm_ranking(
        output_dir=output_dir,
        top_k=None
    )


if __name__ == "__main__":
    main()
