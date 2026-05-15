package com.auggie.student_server.service;

import com.auggie.student_server.entity.Resource;
import com.auggie.student_server.mapper.ResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Auther: auggie
 * @Date: 2026/3/18
 * @Description: ResourceService
 * @Version 1.0.0
 */

@Service
@Transactional
public class ResourceService {
    @Autowired
    private ResourceMapper resourceMapper;

    public List<Resource> findAll() {
        return resourceMapper.findAll();
    }

    public Resource findById(Integer rid) {
        return resourceMapper.findById(rid);
    }

    public List<Resource> findBySearch(Integer rid, Integer ctid, String filename, String description, Integer fuzzy) {
        Resource resource = new Resource();
        resource.setRid(rid);
        resource.setCtid(ctid);
        resource.setFilename(filename);
        resource.setDescription(description);
        fuzzy = (fuzzy == null) ? 0 : fuzzy;

        return resourceMapper.findBySearch(resource, fuzzy);
    }

    public List<Resource> findByCtid(Integer ctid) {
        return resourceMapper.findByCtid(ctid);
    }

    public List<Resource> findByTeacher(Integer tid) {
        return resourceMapper.findByTeacher(tid);
    }

    public List<Resource> findByStudent(Integer sid) {
        return resourceMapper.findByStudent(sid);
    }

    public boolean updateById(Resource resource) {
        return resourceMapper.updateById(resource);
    }

    public boolean save(Resource resource) {
        return resourceMapper.save(resource);
    }

    public boolean deleteById(Integer rid) {
        return resourceMapper.deleteById(rid);
    }
}