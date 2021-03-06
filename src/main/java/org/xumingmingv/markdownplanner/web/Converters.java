package org.xumingmingv.markdownplanner.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.xumingmingv.markdownplanner.model.task.AtomicTask;
import org.xumingmingv.markdownplanner.model.task.CompositeTask;
import org.xumingmingv.markdownplanner.model.Header;
import org.xumingmingv.markdownplanner.model.task.Task;
import org.xumingmingv.markdownplanner.model.Project;

public class Converters {
    public static TaskVO convert(Task task) {
        TaskVO taskVO = new TaskVO();

        taskVO.setId(task.getId());
        taskVO.setParentId(task.getParentId());
        taskVO.setName(task.getName());
        taskVO.setOwner(task.getOwner());
        taskVO.setComposite(false);
        taskVO.setStartDate(task.getStartDate().toString());
        taskVO.setEndDate(task.getEndDate().toString());

        String progress = task.getProgress() + "%";
        if (task.isDelayed()) {
            progress += "(期望:" + task.getExpectedProgress() + "%)";
        }
        taskVO.setProgress(progress);
        taskVO.setRawProgress(task.getProgress());

        taskVO.setManDays(String.format("%.1f", task.getCost() / 2.0));
        // compute background color
        String bgColorClass = "markdownplanner-bg-secondary";
        if (task.isStarted()) {
            bgColorClass = "markdownplanner-bg-primary";
            if (task.isCompleted()) {
                bgColorClass = "markdownplanner-bg-success";
            } else if (task.isDelayed()) {
                bgColorClass = "markdownplanner-bg-warning";
            }
        } else {
            if (task.isDelayed()) {
                bgColorClass = "markdownplanner-bg-danger";
            }
        }
        taskVO.setBgColorClass(bgColorClass);
        taskVO.setComposite(task.isComposite());
        if (!task.isComposite()) {
            taskVO.setLineNumber(((AtomicTask)task).getLineNumber());
        }
        return taskVO;
    }

    public static ProjectVO convert(Project project) {
        Project treenifiedProject = treenify(project);
        ProjectVO projectVO = new ProjectVO();
        projectVO.setTasks(
            treenifiedProject.getTasks().stream()
                .map(Converters::convert)
                .collect(Collectors.toList())
        );
        projectVO.setTotalManDays(treenifiedProject.getTotalStat().getTotalCost() / 2);
        projectVO.setMen(treenifiedProject.getMen());
        projectVO.setProjectStartDate(treenifiedProject.getProjectStartDate().toString());
        projectVO.setProjectEndDate(treenifiedProject.getProjectEndDate().toString());
        projectVO.setStat(treenifiedProject.getStat());

        return projectVO;
    }

    public static Project treenify(Project project) {
        List<Task> tasks = new ArrayList<>();

        // 先建一个“根”任务
        CompositeTask rootTask = new CompositeTask(Header.create(), project.getName(), project.getProjectStartDate());
        rootTask.setId(0);
        rootTask.setParentId(-1);
        Map<Header, CompositeTask> compositeTaskMap = new HashMap<>();
        compositeTaskMap.put(rootTask.getHeader(), rootTask);

        AtomicInteger idCounter = new AtomicInteger();
        for (Task task : project.getTasks()) {
            Header header = task.getHeader();
            addCompositeTaskIfNeeded(tasks, rootTask, compositeTaskMap, idCounter, header);

            CompositeTask parentTask = compositeTaskMap.get(task.getHeader());
            task.setId(idCounter.incrementAndGet());
            task.setParentId(parentTask.getId());
            tasks.add(task);

            addCosts(compositeTaskMap, header, task.getOwner(), task.getCost(), task.getFinishedCost());
        }
        tasks.add(0, rootTask);

        Project treenifiedProject = new Project(project.getName(), project.getProjectStartDate(), tasks, project.getVacations());

        return treenifiedProject;
    }

    private static void addCosts(Map<Header, CompositeTask> compositeTaskMap, Header header, String owner, int cost, double finishedCost) {
        for (int i = 0; i <= header.getHeaders().size(); i++) {
            Header currentHeader = new Header(header.getHeaders().subList(0, i));
            compositeTaskMap.get(currentHeader).addOwnerCost(owner, cost, finishedCost);
        }
    }

    private static void addCompositeTaskIfNeeded(List<Task> tasks, CompositeTask rootTask, Map<Header, CompositeTask> compositeTaskMap,
        AtomicInteger idCounter, Header header) {
        for (int i = 0; i < header.getHeaders().size(); i++) {
            Header currentHeader = new Header(header.getHeaders().subList(0, i + 1));

            if (!compositeTaskMap.containsKey(currentHeader)) {
                CompositeTask currentTask = new CompositeTask(
                    currentHeader, header.getHeaders().get(i).getDisplay(), rootTask.getProjectStartDate()
                );
                currentTask.setId(idCounter.incrementAndGet());
                if (i == 0) {
                    currentTask.setParentId(rootTask.getId());
                } else {
                    currentTask.setParentId(
                        compositeTaskMap.get(
                            new Header(header.getHeaders().subList(0, i))
                        ).getId()
                    );
                }
                compositeTaskMap.put(currentHeader, currentTask);
                tasks.add(currentTask);
            }
        }
    }
}
