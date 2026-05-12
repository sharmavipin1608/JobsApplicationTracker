package com.jobtracker.mapper;

import com.jobtracker.dto.CreateJobRequest;
import com.jobtracker.dto.JobResponse;
import com.jobtracker.model.Job;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JobMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "fitScore", ignore = true)
    @Mapping(target = "status", defaultValue = "UNDETERMINED")
    Job toEntity(CreateJobRequest request);

    JobResponse toResponse(Job job);
}
