function ts_conversion_progress
    set dir_pattern (test -n "$argv[1]"; and echo "$argv[1]"; or echo "src")
    
    # Initialize counters
    set total_flow_lines 0
    set total_ts_lines 0
    
    # Process each matched directory
    for dir in (string split " " (eval echo $dir_pattern))
        if not test -d "$dir"
            echo "Directory not found: $dir"
            continue
        end
        
        echo "Analyzing directory: $dir"
        
        # Count lines in Flow-annotated JS files
        set flow_lines 0
        for js_file in (find "$dir" -name "*.js" -type f)
            if grep -q "//.*flow" "$js_file" || grep -q "/\\*.*flow" "$js_file"
                set file_lines (wc -l < "$js_file" | string trim)
                set flow_lines (math $flow_lines + $file_lines)
            end
        end
        
        # Count lines in TypeScript files
        set ts_lines (find "$dir" \( -name "*.ts" -o -name "*.tsx" \) -type f -exec wc -l {} \; | awk '{sum+=$1} END {print sum}')
        if test -z "$ts_lines"
            set ts_lines 0
        end
        
        # Calculate percentage for this directory
        set total_lines (math $flow_lines + $ts_lines)
        if test $total_lines -gt 0
            set percentage (math "($ts_lines / $total_lines) * 100")
            printf "%-30s %8d Flow JS lines, %8d TypeScript lines - %5.1f%% converted\n" "$dir" $flow_lines $ts_lines $percentage
        else
            printf "%-30s No Flow or TypeScript files found\n" "$dir"
        end
        
        # Add to totals
        set total_flow_lines (math $total_flow_lines + $flow_lines)
        set total_ts_lines (math $total_ts_lines + $ts_lines)
    end
    
    # Calculate overall percentage
    set grand_total (math $total_flow_lines + $total_ts_lines)
    if test $grand_total -gt 0
        set overall_percentage (math "($total_ts_lines / $grand_total) * 100")
        echo "--------------------------------------------------------------------------------"
        printf "OVERALL: %8d Flow JS lines, %8d TypeScript lines - %5.1f%% converted\n" $total_flow_lines $total_ts_lines $overall_percentage
    else
        echo "No Flow or TypeScript files found in the specified directories."
    end
end