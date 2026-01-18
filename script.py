import pandas as pd

def excel_sheet_to_csv(
    excel_file,
    sheet_name,
    output_csv,
    sep=",",
    encoding="utf-8"
):
    # Read the specified sheet
    df = pd.read_excel(excel_file, sheet_name=sheet_name)

    # Write to CSV
    df.to_csv(output_csv, index=False, sep=sep, encoding=encoding)

    print(f"Sheet '{sheet_name}' successfully exported to {output_csv}")


if __name__ == "__main__":
    excel_file = "task280.xlsx"      # Excel file path
    sheet_name = "NodeDetails"           # Sheet name or index (0, 1, ...)
    output_csv = f"{excel_file.replace('.xlsx', '')}_{sheet_name}.csv"       # Output CSV file

    excel_sheet_to_csv(excel_file, sheet_name, output_csv)
