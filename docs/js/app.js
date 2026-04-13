/* docs/js/app.js */

document.addEventListener("DOMContentLoaded", () => {
    // 1. Intersection Observer for Scroll Reveals
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('active');
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.15, rootMargin: "0px 0px -50px 0px" });

    document.querySelectorAll('.reveal, .reveal-left, .reveal-right').forEach(el => observer.observe(el));

    // 2. Parallax Effect logic for images
    document.addEventListener('mousemove', (e) => {
        const parallaxEls = document.querySelectorAll('.img-parallax');
        if (parallaxEls.length > 0) {
            const x = (window.innerWidth - e.pageX * 2) / 80;
            const y = (window.innerHeight - e.pageY * 2) / 80;
            parallaxEls.forEach(el => {
                el.style.transform = `translateX(${x}px) translateY(${y}px) scale(1.05)`;
            });
        }
    });
});
