import axios from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { code, message: msg, data } = response.data;
    if (code !== 200) {
      message.error(msg || '请求失败');
      return Promise.reject(new Error(msg));
    }
    return data;
  },
  (error) => {
    message.error(error.message || '网络错误');
    return Promise.reject(error);
  }
);

export default request;
